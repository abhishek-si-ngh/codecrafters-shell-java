import java.util.*;
import java.util.concurrent.CompletionService;
import java.io.*;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;



public class Main 
{
    public static void main(String[] args) throws Exception 
    {

        Terminal terminal=TerminalBuilder.builder().build();

        LineReader reader=LineReaderBuilder.builder().terminal(terminal).completer(new BuiltinCompleter()).build();
        
        while(true)
        {
            
            String command=reader.readLine("$ ");

            //Parsing command
            String commandParts[]=parseCommand(command);

            Redirection redirection=handleRedirection(commandParts);
            if(redirection!=null)
            {
                commandParts=Arrays.copyOf(commandParts,commandParts.length-2);
            }

            //Getting command name
            String commandName=commandParts[0];

            //Handling builtin/external commands
            if(commandName.equals("exit"))
                break;

            else if(commandName.equals("echo"))
            {
                executeEcho(commandParts,redirection);
            }
            else if(commandName.equals("type"))
            {
                executeType(commandParts);
            }
            else
            {
                executeExternalCommand(commandParts,redirection);
            }
        }
        terminal.close();
    }


    static class BuiltinCompleter implements Completer{
        @Override
        public void complete(LineReader reader,ParsedLine line,List<Candidate> candidates)
        {
            String word=line.word();
            if("echo".startsWith(word))
            {
                candidates.add(new Candidate("echo "));
            }
            if("exit".startsWith(word))
            {
                candidates.add(new Candidate("exit "));
            }
        }
    }


    static class Redirection
    {
        String operator;
        String outPutFile;
        Redirection(String operator,String outPutFile)
        {
            this.operator=operator;
            this.outPutFile=outPutFile;
        }
    } 
    //Redirection Detection

    public static Redirection handleRedirection(String commandParts[])
    {
        List<String> commandList=Arrays.asList(commandParts);

        for(int i=0;i<commandList.size();i++)
        {
            String com=commandList.get(i);
            if(com.equals(">") || com.equals("1>") || com.equals("2>") || com.equals(">>") || com.equals("1>>") || com.equals("2>>"))
                if((i+1< commandList.size()))
                {
                    return new Redirection(com, commandList.get(i+1));
                }
        }
        return null;
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
                //Checking if closing quote is found

                if(ch==quoteChar)
                {
                    quoteChar='\u0000';
                    continue;
                }

                //Checking if inside single quotes

                if(quoteChar=='\'')
                {
                    part+=ch;
                }

                //Checking if inside double quotes

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

                //Checking if outside quotes

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

        //Adding the last part of the command if it is not empty

        if(part.length()>0)
            arguments.add(part);
        return arguments.toArray(new String[0]);
    }


    
    //Method to execute echo command

    public static void executeEcho(String commandParts[],Redirection redirection) throws Exception
    {
        PrintStream originalOut=System.out;
        PrintStream redirectedOut=null;
        try{

            if(redirection!=null)
            {
                if(redirection.operator.equals(">") || redirection.operator.equals("1>"))
                {
                    redirectedOut=redirectStdout(redirection.outPutFile);
                    System.setOut(redirectedOut);
                }
                else if(redirection.operator.equals("2>"))
                {
                    createOrTruncateFile(redirection.outPutFile);
                }
                else if(redirection.operator.equals(">>") || redirection.operator.equals("1>>"))
                {
                    redirectedOut=redirectStdoutAppend(redirection.outPutFile);
                    System.setOut(redirectedOut);
                }
                else if(redirection.operator.equals("2>>"))
                {
                    createOrAppendFile(redirection.outPutFile);
                }
            }
            for(int i=1;i<commandParts.length;i++)
            {
                if(i!=commandParts.length-1)
                    System.out.print(commandParts[i]+" ");
                else
                    System.out.print(commandParts[i]);
            }
            System.out.println();
        }
        catch( Exception e)
        {
            System.out.println("Error "+e.getMessage());
        }
        finally{
            System.setOut(originalOut);
            if(redirectedOut!=null)
                redirectedOut.close();
        }
    }

    public static PrintStream redirectStdout(String file)throws FileNotFoundException
    {
        return new PrintStream(new FileOutputStream(file),true);
    }

    public static PrintStream redirectStdoutAppend(String file)throws FileNotFoundException
    {
        return new PrintStream(new FileOutputStream(file,true),true);
    }

    public static void createOrTruncateFile(String file)throws IOException
    {
        new FileOutputStream(file).close();
    }

    public static void createOrAppendFile(String file)throws IOException
    {
        new FileOutputStream(file, true).close();
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

    public static void executeExternalCommand(String commandParts[],Redirection redirection) throws Exception
    {
        Path executable=findExecutable(commandParts[0]);

        if(executable!=null) 
        {
            ProcessBuilder pb=new ProcessBuilder(commandParts);

            if(redirection!=null)
            {
                if(redirection.operator.equals(">") || redirection.operator.equals(("1>")))
                {
                    pb.redirectOutput(ProcessBuilder.Redirect.to(new File(redirection.outPutFile)));
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }
                else if(redirection.operator.equals("2>"))
                {
                    pb.redirectError(ProcessBuilder.Redirect.to(new File(redirection.outPutFile)));
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
                else if(redirection.operator.equals(">>") || redirection.operator.equals("1>>"))
                {
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(redirection.outPutFile)));
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }
                else if(redirection.operator.equals("2>>"))
                {
                    pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(redirection.outPutFile)));
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }
            else
                pb.inheritIO();

            Process p=pb.start();
            p.waitFor();
        }
        else
            System.out.println(commandParts[0]+": command not found");
    }
}
