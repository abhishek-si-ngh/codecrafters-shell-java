import java.util.*;
import java.io.*;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;



public class Main 
{
    public static void main(String[] args) throws Exception 
    {

        Scanner sc=new Scanner(System.in);
        
        while(true)
        {
            System.out.print("$ ");
            
            String command=sc.nextLine();

            //Parsing command
            String commandParts[]=parseCommand(command);

            //Getting command name
            String commandName=commandParts[0];

            //Handling builtin/external commands
            if(commandName.equals("exit"))
                break;

            else if(commandName.equals("echo"))
            {
                executeEcho(commandParts);
            }
            else if(commandName.equals("type"))
            {
                executeType(commandParts);
            }
            else
            {
                executeExternalCommand(commandParts);
            }
        }
        sc.close();
    }



    

    //Command parsing method

    public static String[] parseCommand(String command)
    {
        ArrayList<String> arguments=new ArrayList<>();
        String part="";
        char quoteChar='\u0000';

        for(int i=0;i<command.length();i++)
        {
            char ch=command.charAt(i);
            if((ch=='\'' || ch=='\"') && quoteChar=='\u0000')
            {
                quoteChar=ch;
            }
            else
            {
                if(ch==quoteChar)
                {
                    quoteChar='\u0000';
                    continue;
                }
                if(quoteChar=='\'')
                {
                    part+=ch;
                }
                else if(quoteChar=='\"')
                {
                    if(ch=='\\')
                    {
                        if(i!=command.length()-1 && (command.charAt(i+1)=='\"' || command.charAt(i+1)=='\\'))
                        {
                            part+=command.charAt(i+1);
                            i++;
                        }
                        else
                            part+=ch;
                    }
                    else
                        part+=ch;
                }
                else
                {
                    if(Character.isWhitespace(ch))
                    {
                            if(part.length()>0)
                                arguments.add(part);
                            part="";

                    }
                    else
                    {
                        if(ch=='\\')
                        {
                            if(i!=command.length()-1)
                            {
                                part+=command.charAt(i+1);
                            }
                            i++;
                            continue;
                        }
                        part+=ch;
                    }
                }
            }
        }
        if(part.length()>0)
            arguments.add(part);
        return arguments.toArray(new String[0]);
    }


    
    //Method to execute echo command

    public static void executeEcho(String commandParts[])
    {
        for(int i=1;i<commandParts.length;i++)
        {
            if(i!=commandParts.length-1)
                System.out.print(commandParts[i]+" ");
            else
                System.out.print(commandParts[i]);
        }
        System.out.println();
    }


    //Meethod to execute type command

    public static void executeType(String commandParts[])
    {
        String target=commandParts[1];
        if(target.equals("exit"))
            System.out.println(target+" is a shell builtin");
        else if(target.equals("echo"))
            System.out.println(target+" is a shell builtin");
        else if(target.equals("type"))
            System.out.println(target+" is a shell builtin");
        else
        {
            Path result=findExecutable(target);
            if(result!=null)
            {
                System.out.println(target+" is "+result);
            }
            else
            System.out.println(target+": not found");
        }
    }


    //Method to find executable command in path

    public static Path findExecutable(String commandName)
    {
        String path=System.getenv("PATH");

        if(path==null)
        return null;

        String directories[]=path.split(File.pathSeparator);
        for(String dir:directories)
        {
            Path newPath=Paths.get(dir,commandName);
            if(Files.exists(newPath) && Files.isExecutable(newPath))
                return newPath;
        }
        return null;
    }


    //Method to execute external commands

    public static void executeExternalCommand(String commandParts[]) throws Exception
    {
        Path executable=findExecutable(commandParts[0]);

        if(executable!=null) 
        {
            ProcessBuilder pb=new ProcessBuilder(commandParts);
            pb.inheritIO();
            Process p=pb.start();
            p.waitFor();
        }
        else
        System.out.println(commandParts[0]+": command not found");
    }
}

