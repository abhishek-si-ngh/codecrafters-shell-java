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
import java.nio.charset.StandardCharsets;



public class Main 
{
    static Map<String,String> completionMap=new HashMap<>();
    static List<Job> jobs=new ArrayList<>();
    static int nextJobNumber=1;
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
            reapCompletedJobs();
            
            String command=reader.readLine("$ ");

            //Parsing command
            String commandParts[]=parseCommand(command);

            boolean background=false;
            if(commandParts.length>0 && commandParts[commandParts.length-1].equals("&"))
            {
                background=true;
                commandParts=Arrays.copyOf(commandParts,commandParts.length-1);
            }


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
            else if(commandName.equals("complete"))
            {
                executeComplete(commandParts);
            }
            else if(commandName.equals("jobs"))
            {
                executeJobs();
            }
            else
            {
                executeExternalCommand(commandParts,redirection,background,command);
            }
        }
        terminal.close();
    }

    //Command Completion
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
        if("jobs".startsWith(word))
            matches.add("jobs");

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



    //Longest Matching Prefix finding method
    public static String longestCommonPrefix(Set<String> matches)
    {
        String matchString[]=matches.toArray(new String[0]);
        String prefix=matchString[0];

        for(String match:matchString)
        {
            while(!match.startsWith(prefix) && !prefix.isEmpty())
            {
                prefix=prefix.substring(0,prefix.length()-1);
            }
        }
        return prefix;
    }



    static class BuiltinCompleter implements Completer
    {
        @Override
        public void complete(LineReader reader,ParsedLine line,List<Candidate> candidates)
        {
            String word=line.word();

            Set<String> matches;

            if(line.wordIndex()>0)
                matches=findFilenameMatches(word);
            else
                matches=findCompletionMatches(word);

            for(String name:matches)
            {
                String suffix=name.endsWith("/")?"":" ";
                candidates.add(new Candidate(name,name,null,null,suffix,null,true));
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
            String buffer=reader.getBuffer().toString();
            int cursor=reader.getBuffer().cursor();

            ParsedLine line=reader.getParser().parse(
            buffer,
            cursor,
            org.jline.reader.Parser.ParseContext.COMPLETE
            );

            String word=line.word();
            if(line.wordIndex()>0 && !line.words().isEmpty())
            {
                String commandName=line.words().get(0);
                String script=completionMap.get(commandName);
                
                
                
                if(script!=null)
                {
                    try
                    {
                        String previousWord="";
                        if(line.wordIndex()>0)
                            previousWord=line.words().get(line.wordIndex()-1);
                        int compPoint=buffer.substring(0,cursor).getBytes(StandardCharsets.UTF_8).length;
                        Set<String> candidates=runCompleterScript(script,commandName,word,previousWord,buffer,compPoint);



                        if(candidates.size()==0)
                        {
                            reader.callWidget(LineReader.BEEP);
                            return true;
                        }

                        if(candidates.size()==1)
                        {
                            String candidate=candidates.iterator().next();

                            reader.getBuffer().backspace(word.length());
                            reader.getBuffer().write(candidate+" ");
                            reader.callWidget(LineReader.REDRAW_LINE);

                            tabCount=0;
                            return true;
                        }

                        if(candidates.size()>1)
                        {
                            String prefix=longestCommonPrefix(candidates);

                            if(prefix.length()>word.length() && prefix.startsWith(word))
                            {
                                String addition=prefix.substring(word.length());

                                reader.getBuffer().write(addition);
                                reader.callWidget(LineReader.REDRAW_LINE);

                                tabCount=0;
                                return true;
                            }


                            tabCount++;
    
                            if(tabCount==1)
                            {
                                reader.callWidget(LineReader.BEEP);
                                return false;
                            }
    
    
                            if(tabCount>=2)
                            {
                                String currentBuffer=reader.getBuffer().toString();
    
                                reader.getTerminal().writer().print("\r\n");
                                reader.getTerminal().writer().println(String.join("  ",candidates));
                                reader.getTerminal().writer().print("$ "+currentBuffer);
                                reader.getTerminal().writer().flush();
    
                                return true;
                            }
                        }


                        // reader.getBuffer().backspace(word.length());
                        // reader.getBuffer().write(candidate+" ");
                        // reader.callWidget(LineReader.REDRAW_LINE);
                        // return true;
                    }
                    catch(Exception e)
                    {
                        reader.callWidget(LineReader.BEEP);
                        return true;
                    }
                }
            }
            
            if(!buffer.equals(lastBuffer))
            {
                    lastBuffer=buffer;
                    tabCount=0;
            }
                
                
            Set<String> matches;
                
            if(line.wordIndex()>0)
                matches=findFilenameMatches(word);
            else
                matches=findCompletionMatches(word);
                
                
            // No matches:
            // Let JLine perform normal completion.
            // It will ring the bell because there are no candidates.
            if(matches.size()==0)
            {
                reader.callWidget(LineReader.BEEP);
                // tabCount=0;
                return true;
            }
                
            // Exactly one match:
            // Let JLine perform normal completion.
            if(matches.size()==1)
            {
                reader.callWidget(LineReader.COMPLETE_WORD);
                // tabCount=0;
                return true;
            }
                
                
            // Multiple matches
                
            if(matches.size()>1)
            {
                String prefix=longestCommonPrefix(matches);
                
                //LCP gives us more charachters
                
                if(!prefix.equals(word))
                {
                    String addition=prefix.substring(word.length());
                    reader.getBuffer().write(addition);
                    reader.callWidget(LineReader.REDRAW_LINE);
                
                    tabCount=0;
                    return true;
                }
                
                tabCount++;
                                
                if(tabCount==1)
                {
                    reader.callWidget(LineReader.BEEP);
                    return false;
                }
                
                if(tabCount>=2)
                {
                    String currentBuffer=reader.getBuffer().toString();
                
                    reader.getTerminal().writer().print("\r\n");
                    reader.getTerminal().writer().println(String.join(" ",matches));
                    reader.getTerminal().writer().print("$ "+currentBuffer);
                    reader.getTerminal().writer().flush();
                
                    return true;
                }
                
            }
            return true;
        }
    }

    static class Job
    {
        int jobNumber;
        Process process;
        String command;
        String status;

        Job(int jobNumber,Process process,String command,String status)
        {
            this.jobNumber=jobNumber;
            this.process=process;
            this.command=command;
            this.status=status;
        }
    }


    //Script Running method
    public static Set<String> runCompleterScript(String script,String commandName,String word,String previousWord,String compLine,int compPoint)throws Exception
    {
        ProcessBuilder pb=new ProcessBuilder(script,commandName,word,previousWord);
        pb.environment().put("COMP_LINE", compLine);
        pb.environment().put("COMP_POINT", String.valueOf(compPoint));

        Process process=pb.start();

        BufferedReader br=new BufferedReader(new InputStreamReader(process.getInputStream()));

        Set<String> candidates=new TreeSet<>();

        String candidate;

        while((candidate=br.readLine())!=null)
        {
            if(!candidate.isEmpty())
                candidates.add(candidate);
        }
        process.waitFor();

        return candidates;
    }


    //File name Completion
    public static Set<String> findFilenameMatches(String word)
    {
        Set<String> matches=new TreeSet<>();

        // if(word.isEmpty())
        //     return matches;

        String directoryPath=".";
        String prefix=word;
        int lastSlash=word.lastIndexOf('/');

        if(lastSlash!=-1)
        {
            directoryPath=word.substring(0, lastSlash+1);
            prefix=word.substring(lastSlash+1);
        }
        File directory=new File(directoryPath);
        File files[]=directory.listFiles();
        
        if(files==null)
            return matches;
        
        for(File file:files)
        {
            String name=file.getName();

            if(name.startsWith(prefix))
            {
                if(file.isDirectory())
                {
                    if(lastSlash!=-1)
                        matches.add(directoryPath+name+"/");
                    else
                        matches.add(name+"/");
                }
                else if(file.isFile())
                {
                    if(lastSlash!=-1)
                        matches.add(directoryPath+name);
                    else
                        matches.add(name);
                }
            }
        }
                //Builtins
                
                // if("echo".startsWith(word))
                //     matches.add("echo");
                // if("exit".startsWith(word))
                //     matches.add("exit");
                
                //External executables
                
                // String path=System.getenv("PATH");
                
                // if(path!=null)
                // {
                //     String directories[]=path.split(File.pathSeparator);
                
                //     for(String dir:directories)
                //     {
                //         File directory=new File(".");
                //         File files[]=directory.listFiles();
                
                //         if(files==null)
                //             continue;
                
                //         for(File file:files)
                //         {
                //             String name=file.getName();
                
                //             if(file.isFile() && file.canExecute() && name.startsWith(word))
                //             {
                //                 matches.add(name);
                //             }
                //         }
                //     }
                // }
        // int lastSlash=word.lastIndexOf('\\');
        // String directoryPath=word.substring(0, lastSlash);
        // String prefix=word.substring(lastSlash+1);
        
        // File directory=new File(directoryPath);
        // File files[]=directory.listFiles();
        
        // if(files==null)
        //     return matches;
        
        // for(File file:files)
        // {
        //     String name=file.getName();
        //     if(file.isFile() && name.startsWith(prefix))
        //     {
        //         matches.add(directoryPath+name);
        //     }
        // }
        
        return matches;
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
        else if(target.equals("complete"))
            System.out.println(target+" is a shell builtin");
        else if(target.equals("jobs"))
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

    //Method to Execute Complete command

    public static void executeComplete(String commandParts[])
    {
        if(commandParts.length>=4 && commandParts[1].equals("-C"))
        {
            String path=commandParts[2];
            String commandName=commandParts[3];
            completionMap.put(commandName, path);
        }
        else if(commandParts.length>=3 && commandParts[1].equals("-p"))
        {
            String commandName=commandParts[2];
            String path=completionMap.get(commandName);
            if(path!=null)
            {
                System.out.println("complete -C \'"+path+"\' "+commandName);
            }
            else
                System.out.println("complete: "+commandName+": no completion specification");
        }
        else if(commandParts.length>=3 && commandParts[1].equals("-r"))
        {
            String commandName=commandParts[2];
            completionMap.remove(commandName);
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

    public static void executeJobs()throws InterruptedException
    {
        // Iterator<Job> iterator=jobs.iterator();

        // while(iterator.hasNext())
        // {
        //     Job job=iterator.next();

        //     if(job.process.isAlive())
        //     {
        //         System.out.printf("[%d]+  %-24s%s%n",job.jobNumber,"Running",job.command);
        //     }
        //     else
        //     {
        //         job.process.waitFor();
        //         String doneCommand=job.command;
        //         if(doneCommand.endsWith("&"))
        //         {
        //             doneCommand=doneCommand.substring(0,doneCommand.length()-1).trim();
        //         }
        //         System.out.printf("[%d]+  %-24s%s%n",job.jobNumber,"Done",doneCommand);
        //         iterator.remove();
        //     }
        // }

        List<Job> completedJobs=new ArrayList<>();
        for(int i=0;i<jobs.size();i++)
        {
            Job job=jobs.get(i);

            String marker=" ";

            if(i==jobs.size()-1)
                marker="+";
            else if(i==jobs.size()-2)
                marker="-";
            if(job.process.isAlive())
            {
                System.out.printf("[%d]%s  %-24s%s%n",job.jobNumber,marker,"Running",job.command);
            }
            else
            {
                job.process.waitFor();

                String doneCommand=job.command;

                if(doneCommand.endsWith("&"))
                {
                    doneCommand=doneCommand.substring(0,doneCommand.length()-1).trim();
                }

                System.out.printf("[%d]%s  %-24s%s%n",job.jobNumber,marker,"Done",doneCommand);
                completedJobs.add(job);
            }

        }
        jobs.removeAll(completedJobs);
    }

    //Method to reap completed jobs
    public static void reapCompletedJobs()throws InterruptedException
    {
        List<Job> completedJobs=new ArrayList<>();

        for(int i=0;i<jobs.size();i++)
        {
            Job job=jobs.get(i);
            if(!job.process.isAlive())
            {
                job.process.waitFor();

                String marker=" ";

                if(i==jobs.size()-1)
                    marker="+";
                else if(i==jobs.size()-2)
                    marker="-";

                String doneCommand=job.command;

                if(doneCommand.endsWith("&"))
                {
                    doneCommand=doneCommand.substring(0,doneCommand.length()-1).trim();
                }

                System.out.printf("[%d]%s  %-24s%s%n",job.jobNumber,marker,"Done",doneCommand);
                completedJobs.add(job);
            }
        }
        jobs.removeAll(completedJobs);
    }

    //MAthod to calculate next job number
    public static int getNextJobNumber()
    {
        if(jobs.isEmpty())
        return 1;

        int maxJobNumber=0;

        for(Job job:jobs)
        {
            if(job.jobNumber>maxJobNumber)
            {
                maxJobNumber=job.jobNumber;
            }
        }
        return maxJobNumber+1;
    }

    //Method to execute external commands

    public static void executeExternalCommand(String commandParts[],Redirection redirection,boolean background,String command) throws Exception
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

            if(background)
            {
                int jobNumber=getNextJobNumber();
                System.out.println("["+jobNumber+"] "+p.pid());
                jobs.add(new Job(jobNumber,p,command,"Running"));
            }
            else
            p.waitFor();
        }
        else
            System.out.println(commandParts[0]+": command not found");
    }
}
