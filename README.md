Repo is archived. New repo is here: https://github.com/Bible-Translation-Tools/BTT-Writer-Android

BTT-Writer Android
------------------

A tool to translate the Bible into your own language. 

## Quck Start
First make sure you have all the dependencies installed

* [Android Studio](http://developer.android.com/sdk/index.html)
* [Virtual Box](https://www.virtualbox.org/) (you may skip this and the rest of these steps if using a physical device)
* [Genymotion](http://www.genymotion.com/)
* [Genymotion Android Studio Plugin](https://cloud.genymotion.com/page/doc/#collapse-intellij)

Then fork this repository and clone your fork. After the repository has been cloned to your computer create a new Genymotion emulator. These emulators are faster and have better features that the stock emulators that come with the Androd SDK. As always a physical device will provide the best experience if you have one.

API 15 is currently the standard minimum sdk version so it is likely you will have it once [Android Studio] has been installed. If not then you will need to download it following the instructions in [Adding SDK Packages].

For more information please read the [wiki].

## Building
This application relies on several native libraries. Therefore when building for a device or emulator you must choose the correct build varient for that platform. In Android Studio you can change the build varients by clicking on the `Build Variants` tab in the lower left corner of the IDE window. This will display an embeded window in which you can choose the correct build variant for the `app` module.

In most cases you should use the `x86Debug` variant for emulators and the `fatDebug` variant for physical devices.

[Virtual Box]:https://www.virtualbox.org/
[Genymotion Android Studio Plugin]:https://cloud.genymotion.com/page/doc/#collapse-intellij
[Adding SDK Packages]:http://developer.android.com/sdk/installing/adding-packages.html
[Genymotion]:http://www.genymotion.com/
[Android Studio]:https://developer.android.com/sdk/installing/studio.html
[Code Style Guidelines]:https://source.android.com/source/code-style.html
[wiki]:https://github.com/unfoldingWord-dev/ts-android/wiki
