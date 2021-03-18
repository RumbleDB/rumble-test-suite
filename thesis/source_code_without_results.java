private boolean AssertEq(List<Item> resultAsList,
        XdmNode assertion) throws UnsupportedTypeException {
    String assertExpression = assertion.getStringValue();
    List<String> lines = resultAsList.stream()
       .map(x -> x.serialize()).collect(Collectors.toList());
    assertExpression += "=" + lines.get(0);
    List<Item> nestedResult = runQuery(assertExpression);
    return AssertTrue(nestedResult);
}

private boolean AssertStringValue(List<Item> resultAsList,
        XdmNode assertion) throws UnsupportedTypeException{
    String assertExpression = assertion.getStringValue();
    List<String> lines = resultAsList.stream()
       .map(x -> x.serialize()).collect(Collectors.toList());
    return assertExpression.equals(String.join(" ",lines));
}