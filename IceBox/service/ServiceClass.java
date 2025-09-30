class ServiceClass {

    public String processData(String input) {
        String reversed = utilityClass.reverseString(input);
        String nome = "João";
        return utilityClass.toUpperCase(reversed);
    }

    private UtilityClass utilityClass = new UtilityClass();
}
