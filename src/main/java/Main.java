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
import org.jline.reader.Widget;
import org.jline.keymap.KeyMap;



public class Main 
{
    public static void main(String[] args) throws Exception 
    {

        Terminal terminal=TerminalBuilder.builder().build();

        LineReader reader=LineReaderBuilder.builder().terminal(terminal).completer(new BuiltinCompleter()).option(LineReader.Option.DISABLE_EVENT_EXPANSION,true).option(LineReader.Option.AUTO_LIST,false).option(LineReader.Option.LIST_AMBIGUOUS,true).option(LineReader.Option.AUTO_MENU,false).build();

        TabCompletionWidget tabWidget=new TabCompletionWidget(reader);

        reader.getKeyMaps()
        .get(LineReader.MAIN)
        .bind(tabWidget, KeyMap.ctrl('I'));
        
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

    public static Set<String> findCompletionMatches(String word)
    {
        Set<String> matches=new TreeSet<>();

        if(word.isEmpty())
            return matches;

        //Builtins

        if("echo".startsWith(word))
            matches.add("echo");
        if("exit".startsWith(word))
            matches.add("exit");

        //External executables

        String path=System.getenv("PATH");

        if(path!=null)
        {
            String directories[]=path.split(File.pathSeparator);

            for(String dir:directories)
            {
                File directory=new File(dir);
                File files[]=directory.listFiles();

                if(files==null)
                    continue;

                for(File file:files)
                {
                    String name=file.getName();

                    if(file.isFile() && file.canExecute() && name.startsWith(word))
                    {
                        matches.add(name);
                    }
                }
            }
        }
        return matches;
    }

    static class BuiltinCompleter implements Completer
    {
        @Override
        public void complete(LineReader reader,ParsedLine line,List<Candidate> candidates)
        {
            String word=line.word();

            Set<String> matches=findCompletionMatches(word);

            for(String name:matches)
            {
                candidates.add(new Candidate(name,name,null,null," ",null,true));
            }
        }
    }

    static class TabCompletionWidget implements Widget
    {
        private final LineReader reader;
        private String lastBuffer="";
        private int tabCount=0;

        TabCompletionWidget(LineReader reader)
        {
            this.reader=reader;
        }

        @Override
        public boolean apply()
        {
            //System.out.println("TAB WIDGET CALLED");
            String buffer=reader.getBuffer().toString();
            int cursor=reader.getBuffer().cursor();

            ParsedLine line=reader.getParser().parse(
            buffer,
            cursor,
            org.jline.reader.Parser.ParseContext.COMPLETE
            );

            String word=line.word();

            if(!buffer.equals(lastBuffer))
            {
                lastBuffer=buffer;
                tabCount=0;
            }

            Set<String> matches=findCompletionMatches(word);

            // No matches:
            // Let JLine perform normal completion.
            // It will ring the bell because there are no candidates.
            if(matches.size()==0)
            {
                reader.callWidget(LineReader.BEEP);
                tabCount=0;
                return true;
            }

            // Exactly one match:
            // Let JLine perform normal completion.
            if(matches.size()==1)
            {
                reader.callWidget(LineReader.COMPLETE_WORD);
                tabCount=0;
                return true;
            }


            // Multiple matches

            if(matches.size()>1)
            {
                tabCount++;
    
                // First TAB → return false.
                // JLine will ring the bell automatically.
                if(tabCount==1)
                {
                    reader.callWidget(LineReader.BEEP);
                    return false;
                }
    
                // Second TAB → print matches above the prompt.
                if(tabCount==2)
                {
                    //reader.printAbove(String.join("  ",matches));
                    tabCount=0;

                    String currentBuffer=reader.getBuffer().toString();

                    reader.getTerminal().writer().print("\r\n");
                    reader.getTerminal().writer().println(String.join("  ",matches));
                    reader.getTerminal().writer().print("$ "+currentBuffer);
                    reader.getTerminal().writer().flush();

                    // reader.callWidget(LineReader.CLEAR);

                    // reader.getTerminal().writer().println(String.join(" ",matches));
                    // //reader.callWidget(LineReader.LIST_CHOICES);
                    // reader.callWidget(LineReader.REDRAW_LINE);
                    // reader.callWidget(LineReader.REDISPLAY);

                    // reader.getTerminal().writer().flush();

                    //reader.printAbove(String.join("  ",matches));

                    return true;
                }
            }

            return true;
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
