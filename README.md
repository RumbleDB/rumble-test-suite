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

# Reading the report
If you run the pipeline all test results are shown in the tests tab on gitlab. Clicking on a testcase gives you more information. The same information can also be accessed in the xml reports directly. Most info like assertion, original testcase etc are logged in ``<system-out>`` using the following pattern:

```[[key|value]]```

The most important keys and the meaning of their values are:
- ``originalTest`` contains the original text of the testcase
- ``originalAssertion`` contains the original assertion of the testcase (before possible modification)
- ``convertedString`` contains the converted assertion or testcase text
- ``query`` a query that is evaluated by rumbleDB (can possibly be multiple per testcase incase there are multiple assertions)
- ``category`` can be 
  - ``PASS`` testcase passed
  - ``MANAGED`` testcase passed with modification
  - ``FAIL`` testcase ran but output didnt fullfill assertion
  - ``ERROR`` testcase threw error that is not a part of the skip reasons
  - ``SKIP`` testcase was skipped due to a skip reason

The current skip reasons are:
- Parser error XPST0003 (we assume that an unimplemented feature was encountered)
- Method or type constructor not implemented error XPST0017
- Type not implemented error XPST0051
- Unsupported errorcode in assertion (based on hardcoded list in Constants.java)
- Testcase or Testset is on list of Testcases/Testsets to skip (hardcoded list in Constants.java)

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


# Notes
This work was started as part of a master's thesis by Stevan Mihajlovic. To view the original state of that final work, check out the ``stevan-master`` branch.