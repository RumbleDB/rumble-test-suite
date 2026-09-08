# Introduction
This project aims to run the entire W3C QT3 test suite for XQuery on RumbleDB. There are two ways of running this testsuite:
1. Run against the JSONiq parser in RumbleDB. This is the default option and converts some XQuery specific things to JSONiq++ for it to work. 
2. Run against the XQuery parser in RumbleDB. This skips conversions and specifies RumbleDB to use its XQuery parser.

# Usage
There is a pipeline setup that allows you to run the testsuite against any branch in the gitlab rumble repo. To run the tests against your branch follow these steps:
1. In the gitlab webinterface on the left, go to build -> pipelines and choose "new pipeline" in the top right
2. Add a variable with the *input variable key* ``TESTED_BRANCH`` and the *input variable value* of your branch name that you want to test. Default is ``master``
3. Optionally add a variable ``TESTED_PARSER`` with the value "xquery" if you want to test the XQuery parser. By default it tests the JSONiq parser.
4. Click "run pipeline"

This will run the pipeline with all the tests. To view your results, go to build -> pipelines and click on the status of the newest pipeline. Now you will see a tab called tests at the top which you can click on to see all the results of all the different parts of the testsuite.

You can also access and download the xml test reports directly by selecting the artifacts of the collect-artifacts job.

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
  - ``FAIL`` testcase ran but output didnt fullfill assertion
  - ``ERROR`` testcase or test harness threw an unexpected error
  - ``SKIP`` testcase was not applicable because of a QT3 dependency

Tests are skipped only when their QT3 dependency metadata says that they do not apply to the current implementation or configuration. Runtime errors, unsupported harness assertions, timeouts, and other implementation-specific problems are reported as failures or errors rather than skips.

# View Analytics
There are some analytics that are run after the tests are evaluated. You can view the resulting plots (and the complete JSON files) by browsing the artifacts of the *plot* job.

Currently implemented analytics:
- **Overview.png** Overview how many tests pass, fail, error, skip in general and per testset
- **failure_types.png** Shows the most frequent failures
- **error_types.png** Shows the most frequent errors
- **failure_types.png** Shows the most frequent reasons for skips


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
mvn -Dtest=Prod1Test test
```
Or run a single named QT3 test case by passing its test-case name:
```
mvn -Dtest=OpTest -Dtest.case=fn-union-node-args-002 test
```

A selected name is validated against the full QT3 catalog. When running multiple JUnit suite
partitions (for example `-Dtest=Prod1Test,Prod2Test`), partitions without that case are skipped.
Unknown or duplicate names are errors. Select a partition containing the case to execute it;
selecting only unrelated partitions produces skips.

Or all the tests at once with
```
mvn test
```
The output reports will be generated in rumble-test-suite/target/surefire-reports/*

(Tested with Python 3.12)
Finally, analytics can be generated and plotted by running
```
find target/surefire-reports -type f ! -name '*.xml' -delete
mvn compile
mvn exec:java

pip install - r analytics/requirements.txt
python analytics/plot.py
```

# Authors
- Stevan Mihajlovic
- Marco Schöb


# Notes
This work was started as part of a master's thesis by Stevan Mihajlovic. To view the original state of that final work, check out the ``stevan-master`` branch.
