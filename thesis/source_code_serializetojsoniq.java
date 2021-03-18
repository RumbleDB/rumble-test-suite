private String Convert(String testString){
    try {
        return rumbleInstance.serializeToJSONiq(testString);
    } catch (RumbleException re) {
        return testString;
    } catch (Exception e){
        return testString;
    }
}