import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {

        
        while(true){
            System.out.print("$ ");
            
            Scanner sc=new Scanner(System.in);
            String command=sc.nextLine();
            if(command.equals("exit"))
                break;

            else if(command.startsWith("echo "))
            {
                System.out.println(command.subString(5));
            }
            else
            System.out.println(command+": command not found");
        }
    }
}
