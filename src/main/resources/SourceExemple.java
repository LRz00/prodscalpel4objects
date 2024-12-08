public class SourceExemple{
    public String getWelcomMessage(){
        return "BEM-VINDO";
    }
    
    public int getSquare(int num){
        return num * num;
    }
    
    public boolean isEven(int num){
        return num % 2 == 0;
    }
    
    public int threeSquared(){
        return this.getSquare(3);
    }
    
}