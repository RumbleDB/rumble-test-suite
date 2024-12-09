# Introduction
This project aims to run the entire W3C QT3 test suite for XQuery on RumbleDB. There are two ways of running this testsuite:
1. run the testsuite using conversions. This is the default option and converts some XQuery specific things to JSONiq++ for it to work. 
2. (not yet implemented) run the testsuite skipping conversions. This is useful if you want to test the XQuery3.1 parser of Rumble.

# Usage
There is a pipeline setup that allows you to run the testsuite against any branch in the gitlab rumble repo. To run the tests against your branch follow these steps:
1. In the gitlab webinterface on the left, go to build -> pipelines and choose "new pipeline" in the top right
2. Add a variable with the *input variable key* ``TESTED_BRANCH`` and the *input variable value* of your branch name that you want to test.
3. Click "run pipeline"

This will run the pipeline with all the tests. To view your results, go to build -> pipelines and click on the status of the newest pipeline. Now you will see a tab called tests at the top which you can click on to see all the results of all the different parts of the testsuite.

You can also access and download the test reports directly by clicking on a job in the pipeline and then clicking "downlaod" on the right under job artifacts. 

# Local installation
If you want to work on this repo, you can install it locally with the following steps. Note that you need to have maven installed so if that isn't the case, make sure to follow those steps in the installation guide for rumble.
1. ``git clone https://gitlab.inf.ethz.ch/gfourny/rumble-test-suite.git``
2. ``cd rumble-test-suite``
3. ``git clone http://gitlab.inf.ethz.ch/gfourny/rumble.git``
4. ``cd rumble``
5. ``mvn clean compile assembly:single -quiet``
6. ``cd ..``

Then you can run a test folder like this (example for *prod*)
```
mvn -Dtest=ProdTest test
```
The output reports will be generated in rumble-test-suite/target/surefire-reports/*

# Authors
- Stevan Mihajlovic
- Marco Schöb