package org.unfoldingword.resourcecontainer;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.resourcecontainer.errors.InvalidRCException;
import org.unfoldingword.resourcecontainer.errors.MissingRCException;
import org.unfoldingword.resourcecontainer.errors.OutdatedRCException;
import org.unfoldingword.resourcecontainer.errors.RCException;
import org.unfoldingword.resourcecontainer.errors.UnsupportedRCException;
import org.unfoldingword.tools.jtar.TarInputStream;
import org.unfoldingword.tools.jtar.TarOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an instance of a resource container.
 */
public class ResourceContainer {
    public static final String version = "0.1";
    public static final String slugDelimiter = "_";
    public static final String fileExtension = "tsrc";
    public static final String baseMimeType = "application/tsrc";

    private static final String CONTENT_DIR = "content";

    /**
     * Returns the path to the resource container directory
     */
    public final File path;

    /**
     * Returns the resource container package information.
     * This is the package.json file
     */
    public final JSONObject info;

    /**
     * Returns the resource container data configuration.
     * This is the config.yml file under the content/ directory
     */
    public final Map config;

    /**
     * Returns the table of contents.
     * This is the toc.yml file under the content/ directory.
     * This can be a list or a map.
     */
    public final Object toc;

    /**
     * Returns the slug of the resource container
     */
    public final String slug;

    /**
     * The language represented in this resource container
     */
    public final Language language;

    /**
     * The project represented in this resource container
     */
    public final Project project;

    /**
     * The resource represented in this resource container
     */
    public final Resource resource;

    /**
     * The time this resource container was last modified
     */
    public final int modifiedAt;

    /**
     * The mimetype of the content within this resource container
     */
    public final String contentMimeType;

    /**
     * Instantiates a new resource container object
     * @param containerDirectory the directory of the resource container
     * @param containerInfo the resource container info (package.json)
     * @throws JSONException
     */
    private ResourceContainer(File containerDirectory, JSONObject containerInfo) throws Exception {
        if(!containerInfo.has("modified_at")) throw new InvalidRCException("Missing field: modified_at");
        if(!containerInfo.has("content_mime_type")) throw new InvalidRCException("Missing field: content_mime_type");
        if(!containerInfo.has("language")) throw new InvalidRCException("Missing field: language");
        if(!containerInfo.has("project")) throw new InvalidRCException("Missing field: project");
        if(!containerInfo.has("resource")) throw new InvalidRCException("Missing field: resource");


        this.path = containerDirectory;
        this.info = containerInfo;
        this.modifiedAt = info.getInt("modified_at");
        this.contentMimeType = info.getString("content_mime_type");
        this.language = Language.fromJSON(containerInfo.getJSONObject("language"));
        this.project = Project.fromJSON(containerInfo.getJSONObject("project"));
        this.project.languageSlug = language.slug;
        this.resource = Resource.fromJSON(containerInfo.getJSONObject("resource"));
        this.resource.projectSlug = project.slug;
        this.slug = ContainerTools.makeSlug(language.slug, project.slug, resource.slug);

        // load config
        File configFile = new File(containerDirectory, CONTENT_DIR + "/config.yml");
        Map tempConfig = null;
        if(configFile.exists()) {
            try {
                YamlReader reader = new YamlReader(new FileReader(configFile));
                Object object = reader.read();
                tempConfig = (Map) object;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (YamlException e) {
                e.printStackTrace();
            } finally {
                if (tempConfig == null) tempConfig = new HashMap();
            }
        } else {
            tempConfig = new HashMap();
        }
        this.config = tempConfig;

        // load toc
        File tocFile = new File(containerDirectory, CONTENT_DIR + "/toc.yml");
        Object tempToc = null;
        if(tocFile.exists()) {
            try {
                YamlReader reader = new YamlReader(new FileReader(tocFile));
                tempToc = reader.read();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (YamlException e) {
                e.printStackTrace();
            } finally {
                if (tempToc == null) tempToc = new HashMap();
            }
        } else {
            tempToc = new HashMap<>();
        }
        this.toc = tempToc;
    }

    /**
     * Loads a resource container from the disk.
     * Rejects with an error if the container is not supported. Or does not exist, or is not a directory.
     * @param containerDirectory
     * @throws Exception
     * @return
     */
    public static ResourceContainer load(File containerDirectory) throws Exception {
        if(!containerDirectory.exists()) throw new MissingRCException("The resource container does not exist");
        if(!containerDirectory.isDirectory()) throw new MissingRCException("Not an open resource container");
        File packageFile = new File(containerDirectory, "package.json");
        if(!packageFile.exists()) throw new InvalidRCException("Missing package.json file");
        JSONObject packageJson = new JSONObject(FileUtil.readFileToString(packageFile));
        if(!packageJson.has("package_version")) throw new InvalidRCException("Missing package_version");
        if(Semver.gt(packageJson.getString("package_version"), ResourceContainer.version)) throw new UnsupportedRCException("Unsupported container version");
        if(Semver.lt(packageJson.getString("package_version"), ResourceContainer.version)) throw new OutdatedRCException("Outdated container version");

        return new ResourceContainer(containerDirectory, packageJson);
    }

    /**
     * Creates a new resource container.
     * Rejects with an error if the container exists.
     * @param containerDirectory
     * @param opts
     * @throws Exception
     * @return
     */
    public static ResourceContainer make(File containerDirectory, JSONObject opts) throws Exception {
        if(containerDirectory.exists()) throw new Exception("Resource container directory already exists");
        // TODO: finish this
        throw new Exception("Not implemented yet!");
    }


    /**
     * Opens an archived resource container.
     * If the container is already opened it will be loaded
     * @param containerArchive
     * @param containerDirectory
     * @throws Exception
     * @return
     */
    public static ResourceContainer open(File containerArchive, File containerDirectory) throws Exception {
        if(containerDirectory.exists()) return load(containerDirectory);

        if(!containerArchive.exists()) throw new MissingRCException("Missing resource container");
        File tempFile = new File(containerArchive + ".tmp.tar");
        FileOutputStream out = null;
        BZip2CompressorInputStream bzIn = null;

        // decompress bzip2
        try {
            FileInputStream fin = new FileInputStream(containerArchive);
            BufferedInputStream in = new BufferedInputStream(fin);
            out = new FileOutputStream(tempFile);
            bzIn = new BZip2CompressorInputStream(in);
            int n;
            final byte[] buffer = new byte[2048];
            while ((n = bzIn.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            FileUtil.deleteQuietly(tempFile);
            throw e;
        } finally {
            if(out != null) out.close();
            if(bzIn != null) bzIn.close();
        }

        // un-pack
        FileInputStream fin = new FileInputStream(tempFile);
        BufferedInputStream in = new BufferedInputStream(fin);
        TarInputStream tin = new TarInputStream(in);
        try {
            containerDirectory.mkdirs();
            TarUtil.untar(tin, containerDirectory.getAbsolutePath());
        } catch (Exception e) {
            FileUtil.deleteQuietly(containerDirectory);
            throw e;
        } finally {
            tin.close();
            FileUtil.deleteQuietly(tempFile);
        }

        return load(containerDirectory);
    }

    /**
     * Closes (archives) a resource container.
     * @param containerDirectory
     * @return the path to the resource container archive
     * @throws Exception
     */
    public static File close(File containerDirectory) throws Exception {
        if(!containerDirectory.exists()) throw new MissingRCException("Missing resource container");
        // pack
        File tempFile = new File(containerDirectory.getAbsolutePath() + ".tmp.tar");
        TarOutputStream tout = new TarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
        try {
            TarUtil.tar(null, containerDirectory.getAbsolutePath(), tout);
        } catch(Exception e) {
            FileUtil.deleteQuietly(tempFile);
            throw e;
        } finally {
            tout.close();
        }

        // compress
        File archive = new File(containerDirectory.getAbsolutePath() + "." + ResourceContainer.fileExtension);
        BZip2CompressorOutputStream bzOut = null;
        BufferedInputStream in = null;

        try {
            FileInputStream fin = new FileInputStream(tempFile);
            in = new BufferedInputStream(fin);
            FileOutputStream out = new FileOutputStream(archive);
            bzOut = new BZip2CompressorOutputStream(out);
            int n;
            byte buffer[] = new byte[2048];
            while ((n = in.read(buffer)) != -1) {
                bzOut.write(buffer, 0, n);
            }
            bzOut.close();
            in.close();
        } catch(Exception e) {
            FileUtil.deleteQuietly(archive);
            throw e;
        } finally {
            if(bzOut != null) bzOut.close();
            if(in != null) in.close();
            FileUtil.deleteQuietly(tempFile);
        }

        return archive;
    }

    /**
     * Returns an un-ordered list of chapter slugs in this resource container
     * @return
     */
    public String[] chapters() {
        String[] chapters = (new File(path, CONTENT_DIR)).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return new File(dir, filename).isDirectory() && !filename.equals("config.yml") && !filename.equals("toc.yml");
            }
        });
        if(chapters == null) chapters = new String[0];
        return chapters;
    }

    /**
     * Returns an un-ordered list of chunk slugs in the chapter
     * @param chapterSlug
     * @return
     */
    public String[] chunks(String chapterSlug) {
        final List<String> chunks = new ArrayList<>();
        (new File(new File(path, CONTENT_DIR), chapterSlug)).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                chunks.add(filename.split("\\.")[0]);
                return false;
            }
        });
        return chunks.toArray(new String[chunks.size()]);
    }

    /**
     * Returns the contents of a chunk.
     * If the chunk does not exist or there is an exception an empty string will be returned
     * @param chapterSlug
     * @param chunkSlug
     * @return
     */
    public String readChunk(String chapterSlug, String chunkSlug) {
        File chunkFile = new File(new File(new File(path, CONTENT_DIR), chapterSlug), chunkSlug + "." + chunkExt());
        if(chunkFile.exists() && chunkFile.isFile()) {
            try {
                return FileUtil.readFileToString(chunkFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    /**
     * Returns the file extension to use for content files (chunks)
     * @return
     */
    private String chunkExt() {
        String defaultExt = "txt";
        try {
            switch (info.getString("content_mime_type")) {
                case "text/usx":
                    return "usx";
                case "text/usfm":
                    return "usfm";
                case "text/markdown":
                    return "md";
                default:
                    // unknown format
                    return defaultExt;
            }
        } catch (JSONException e) {
            return defaultExt;
        }
    }
}
