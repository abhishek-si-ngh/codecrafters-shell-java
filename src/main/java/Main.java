import java.util.Scanner;
import java.io.*;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
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
                String commandName = command.substring(5);
                String path=System.getenv("PATH");
                String directories[]=path.split(File.pathSeparator);

                if(commandName.equals("exit"))
                    System.out.println(commandName+" is a shell builtin");
                else if(commandName.equals("echo"))
                    System.out.println(commandName+" is a shell builtin");
                else if(commandName.equals("type"))
                    System.out.println(commandName+" is a shell builtin");
                else
                {
                    boolean found=false;
                    for(String dir:directories)
                    {
                        Path newPath=Paths.get(dir+"/"+commandName);
                        if(Files.exists(newPath) && Files.isExecutable(newPath))
                        {
                            System.out.println(commandName+" is "+newPath);
                            found=true;
                            break;
                        }
                    }
                    if(found==false)
                    System.out.println(command.substring(5)+": not found");
                }
            }
            else
            {
                boolean found=false;
                String args[]=command.split(" ");
                String path=System.getenv("PATH");
                String directories[]=path.split(File.pathSeparator);
                for(String dir:directories)
                {
                    Path newPath=Paths.get(dir+"/"+args[0]);
                    if(Files.exists(newPath) && Files.isExecutable(newPath))
                    {
                        ProcessBuilder pb=new ProcessBuilder();
                        pb.command(args);
                        pb.inheritIO();
                        Process p=pb.start();
                        p.waitFor();
                        found=true;
                        break;
                    }
                }


                if(found==false)
                System.out.println(command+": command not found");
            }
        }
    }
}
