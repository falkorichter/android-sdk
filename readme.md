
# DEPRECATED !!!!
## Check https://github.com/sensorberg-dev/notifications-sdk-android


[![Build Status](https://www.bitrise.io/app/e6905b8beaf57d31.svg?token=CJmf7u_wYIfobxAeADs_Tg&branch=master)](https://github.com/sensorberg-dev/android-sdk/tree/master)
[ ![Download](https://api.bintray.com/packages/sensorberg/maven/android-sdk/images/download.svg) ](https://bintray.com/sensorberg/maven/android-sdk/_latestVersion)



#Integration

For Integration instructions please visit our developer hub Android SDK page and [developer.sensorberg.com/android](https://developer.sensorberg.com/android)

You are viewing the sources of the 2.x Version of the SDK, the documenation on developer.sensorberg.com will be updated in the coming days, feel free to explore the [bundled app](https://github.com/sensorberg-dev/android-sdk/tree/master/dev-app) inside the SDK. The integration has changed slightly from the 1.x version.


#MVP
There's a minimum implementation on mvp module. That can be a starting point to implement our SDK on your project.

#Build,Test,Deploy

##Dependencies:
* JDK 1.7
* Build tools as stated in the build.gradle files
* check the install_android_dependencies.sh file for a list of currently needed packages.

This project is based on a standard Android Gradle setup. Run

```
	./gradlew tasks
```
to see all the tasks.

```
	./gradlew clean connectedAndroidTest
```
will run all the unit tests.

#Release

Set your release name in the root **build.gradle**. If you want a regular release, leave the **project.ext.RC_VERSION** as an empty string.

paste your credentials in the **bintray.properties** file.

run

``` bash
	./gradlew clean cAT android-sdk:bintrayUpload
```

If you want to test the procedure, change the **dryRun** variable in the **bintrayUpload.gradle** file to *true* temporarely. The --info flag will give you details if you need them.

``` bash
	./gradlew clean android-sdk:bintrayUpload --info
	[...]
	Version 'sensorberg/maven/android-sdk/1.0.0-SNAPSHOT' does not exist. Attempting to creating it...
    (Dry run) Created verion 'sensorberg/maven/android-sdk/1.0.0-SNAPSHOT'.
    Uploading to https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT-javadoc.jar...
    (Dry run) Uploaded to 'https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT-javadoc.jar'.
    Uploading to https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT-sources.jar...
    (Dry run) Uploaded to 'https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT-sources.jar'.
    Uploading to https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT.aar...
    (Dry run) Uploaded to 'https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT.aar'.
    Uploading to https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT.pom...
    (Dry run) Uploaded to 'https://api.bintray.com/content/sensorberg/maven/android-sdk/1.0.0-SNAPSHOT/com/sensorberg/sdk/android-sdk/1.0.0-SNAPSHOT/android-sdk-1.0.0-SNAPSHOT.pom'.
    (Dry run) Pulished verion 'sensorberg/maven/android-sdk/1.0.0-SNAPSHOT'.
    :android-sdk:bintrayUpload (Thread[Daemon worker Thread 2,5,main]) completed. Took 1.708 secs.

```

#License
-------

	The MIT License (MIT)
	
	Copyright (c) 2017 Sensorberg GmbH
	
	Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:
	
	The above copyright notice and this permission notice shall be included in
	all copies or substantial portions of the Software.
	
	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
	THE SOFTWARE.
