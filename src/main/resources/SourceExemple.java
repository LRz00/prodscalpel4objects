public class SourceExemple{
    int number = 3;
    
    public String getWelcomMessage(){
        return "BEM-VINDO";
    }
    
    public int getSquare(int num){
        getWelcomMessage();
        return num * number;
        
    }
    
    public boolean isEven(int num){
        return num % 2 == 0;
    }
    
    public int threeSquared(){
        return this.getSquare(3);
    }
    
}