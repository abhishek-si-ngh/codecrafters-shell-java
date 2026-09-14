import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {

        
        while(true){
            System.out.print("$ ");
            
            Scanner sc=new Scanner(System.in);
            String command=sc.nextLine();
            System.out.println(command+": command not found");
            if(command.equals("exit"))
                break;
        }
    }
}
