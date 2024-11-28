private boolean Assert(List<Item> resultAsList,
        XdmNode assertion) throws UnsupportedTypeException {
    String expectedResult = Convert(assertion.getStringValue());
    return runNestedQuery(resultAsList, expectedResult);
}

private boolean runNestedQuery(List<Item> resultAsList, String expectedResult){
    RumbleRuntimeConfiguration configuration = new RumbleRuntimeConfiguration();
    configuration.setExternalVariableValue(
    Name.createVariableInNoNamespace("result"),
        resultAsList);
    String assertExpression = "declare variable $result external;" + expectedResult;
    Rumble rumbleInstance = new Rumble(configuration);
    List<Item> nestedResult = runQuery(assertExpression, rumbleInstance);
    return AssertTrue(nestedResult);
}