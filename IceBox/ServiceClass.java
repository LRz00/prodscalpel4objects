public class ServiceClass {

    public String processData(String input) {
        String reversed = utilityClass.reverseString(input);
        return utilityClass.toUpperCase(reversed);
    }

    public String toUpperCase(String input) {
        return input.toUpperCase();
    }

    public String reverseString(String input) {
        return new StringBuilder(input).reverse().toString();
    }

    private UtilityClass utilityClass = new UtilityClass();
}
