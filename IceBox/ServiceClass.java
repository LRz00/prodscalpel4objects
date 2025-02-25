class ServiceClass {

    public String processData(String input) {
        String reversed = utilityClass.reverseString(input);
        return utilityClass.toUpperCase(reversed);
    }

    private UtilityClass utilityClass = new UtilityClass();
}
