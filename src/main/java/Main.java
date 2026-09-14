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
                System.out.println(command.substring(5));
            }
            else if(command.startsWith("type "))
            {
                if(command.substring(5).equals("exit"))
                    System.out.println(command.substring(5)+" is a shell builtin");
                else if(command.substring(5).equals("echo"))
                    System.out.println(command.substring(5)+" is a shell builtin");
                else if(command.substring(5).equals("type"))
                    System.out.println(command.substring(5)+" is a shell builtin");
                else
                    System.out.println(command.substring(5)+": command not found");         
            }
            else
            System.out.println(command+": command not found");
        }
    }
}
