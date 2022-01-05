package org.unfoldingword.resourcecontainer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a link to a resource container
 */

public class Link {
    public String title;
    public final String url;
    public final String resource;
    public final String project;
    public final String language;
    public final String arguments;
    public final String protocal;
    public final String chapter;
    public final String chunk;
    public final String lastChunk;

    /**
     * Creates a simple external link
     * @param title the human readable title of the link
     * @param url the external link address
     */
    private Link(String title, String url) {
        this.title = title;
        this.url = url;

        protocal = null;
        resource = null;
        project = null;
        chapter = null;
        chunk = null;
        lastChunk = null;
        arguments = null;
        language = null;
    }

    /**
     * Creates a new resource container link.
     *
     * @param protocal used to indicate if this is a media link
     * @param title the human readable title of the link
     * @param language the language of the linked resource container
     * @param project the project of the linked resource container
     * @param resource the resource of the linked resource container
     * @param arguments the raw arguments on the link
     * @param chapter the chapter in the linked resource container
     * @param chunk the chunk (first one if the arguments included a range of chunks) in the linked resource container
     * @param lastChunk the last chunk (if the arguments included a range of chunks) referenced by this link
     */
    private Link(String protocal, String title, String language, String project, String resource, String arguments, String chapter, String chunk, String lastChunk) {
        this.url = null;
        this.protocal = protocal;
        this.title = title;
        this.language = language;
        this.project = project;
        this.resource = resource;
        this.arguments = arguments;
        this.chapter = chapter;
        this.chunk = chunk;
        this.lastChunk = lastChunk;
    }

    /**
     * Checks if this is an external link
     * @return
     */
    public boolean isExternal() {
        return this.url != null;
    }

    /**
     * Checks if this is a media link
     * @return
     */
    public boolean isMedia() {
        return this.protocal != null;
    }

    /**
     * Checksk if this is a Bible passage link
     * @return
     */
    public boolean isPassage() {
        return this.chapter != null && this.chunk != null;
    }

    /**
     * Returns the formatted passage title e.g. 1:2-3
     * @return
     */
    public String passageTitle() {
        if(isPassage()) {
            String tail = "";
            if(lastChunk != null) tail = "-" + formatNumber(lastChunk);
            return formatNumber(chapter) + ":" + formatNumber(chunk) + tail;
        }
        return null;
    }

    /**
     * Attempts to format the string as a number (without leading 0's)
     * otherwise the original value will be returned.
     * @param value
     * @return
     */
    private String formatNumber(String value) {
        try {
            return Integer.parseInt(value) + "";
        } catch(NumberFormatException e) {}
        return value.trim().toLowerCase();
    }

    /**
     * Parses a link. This could be an external link or a resource container link
     *
     * @param link
     * @throws Exception if the link is invalid
     */
    public static Link parseLink(String link) throws Exception {
        Pattern anonymousPattern = Pattern.compile("\\[\\[([^\\]]*)\\]\\]", Pattern.DOTALL);
        Pattern titledPattern = Pattern.compile("\\[([^\\]]*)\\]\\(([^\\)]*)\\)", Pattern.DOTALL);

        String linkTitle = null;
        String linkPath = link;
        Matcher m;
        int numMatches = 1;

        // find anonymous links
        m = anonymousPattern.matcher(link);
        while(m.find()) {
            if(numMatches > 1) throw new Exception("Invalid link! Multiple links found");
            numMatches ++;
            linkPath = m.group(1).toLowerCase();
        }

        // find titled links
        m = titledPattern.matcher(link);
        numMatches = 1;
        while(m.find()) {
            if(numMatches > 1) throw new Exception("Invalid link! Multiple links found");
            numMatches ++;
            linkTitle = m.group(1);
            linkPath = m.group(2).toLowerCase();
        }

        // process link path
        if(linkPath != null) {
            // external link
            if(linkPath.startsWith("http")) {
                return new Link(linkTitle, linkPath);
            }
            return parseResourceLink(linkTitle, linkPath);
        }

        return null;
    }

    /**
     * Parses a resource container link
     * @param title
     * @param path
     * @return
     */
    private static Link parseResourceLink(String title, String path) throws Exception {
        Pattern pattern = Pattern.compile("^((\\w+):)?\\/?(.*)", Pattern.DOTALL);

        String protocal = null;
        String language = null;
        String project = null;
        String resource = null;
        String chapter = null;
        String chunk = null;
        String lastChunk = null;
        String arguments = null;


        // pull out the protocal
        // TRICKY: also pulls off the first / so our string splitting correctly finds the language
        Matcher m = pattern.matcher(path);
        if(m.find()) {
            protocal = m.group(2);
            path = m.group(3);
        }

        String[] components = path.split("\\/");

        // /chapter
        if(components.length == 1) arguments = components[0];

        // /language/project
        if(components.length > 1) {
            language = components[0];
            project = components[1];
        }

        // /language/project/resource
        if(components.length > 2) {
            language = components[0];
            project = components[1];
            resource = components[2];

            // TRICKY: resource can be skipped
            // /language/project/chapter:chunk
            if(resource.contains(":")) {
                arguments = resource;
                resource = null;
            }
        }

        // /language/project/resource/args
        if(components.length > 3) {
            language = components[0];
            project = components[1];
            resource = components[2];
            arguments = components[3];
            // re-build arguments that had the delimiter
            for(int i = 4; i < components.length - 1; i ++) {
                arguments += "/" + components[i];
            }
        }

        // get chapter:chunk from arguments
        chapter = arguments;
        if(arguments != null && arguments.contains(":")) {
            String[] bits = arguments.split(":");
            chapter = bits[0];
            chunk = bits[1];
        }

        // get last chunk
        if(chunk != null && chunk.contains("-")) {
            String[] bits = chunk.split("-");
            chunk = bits[0];
            lastChunk = bits[1];
        }

        // assume resource
        if(resource == null && project != null) resource = project;

        // nullify empty strings
        protocal = nullEmpty(protocal);
        title = nullEmpty(title);
        language = nullEmpty(language);
        project = nullEmpty(project);
        resource = nullEmpty(resource);
        arguments = nullEmpty(arguments);
        chapter = nullEmpty(chapter);
        chunk = nullEmpty(chunk);
        lastChunk = nullEmpty(lastChunk);

        // validate chunks
        if(chunk != null && chunk.contains(",")
                || lastChunk != null && lastChunk.contains(",")) throw new Exception("Invalid passage link " + path);

        if(project != null && resource  != null || arguments != null) {
            return new Link(protocal, title, language, project, resource, arguments, chapter, chunk, lastChunk);
        }
        return null;
    }


    /**
     * Returns the value if it is not empty otherwise null
     * @param value
     * @return
     */
    private static String nullEmpty(String value) {
        if(value != null && value.isEmpty()) return null;
        return value;
    }

    /**
     * Returns a list of links found in the text.
     * This is used to turn inline Bible passages into links.
     * The returned links will include their position within the charsequence
     *
     * @param text the text that will be searched for Bible passages
     * @return
     */
    public static List<Link> findLinks(CharSequence text) {
        // TODO: 10/11/16 automatically parse bible passages.
        return null;
    }
}
