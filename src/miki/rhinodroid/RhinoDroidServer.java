package miki.rhinodroid;

import android.app.*;
import android.os.*;
import android.util.*;
import android.widget.*;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.*;

import org.mozilla.javascript.*;

/**
 * Experimental "web server" that acceps PUT-requests with JavaScript code as content.
 * The server evaluates the JavaScript code using the Rhino engine.
 * Copyright (c) 2009 Mikael Kindborg mikael.kindborg@gmail.com
 * Licence: MIT
 *
 * TODO: 
 * Clean up the HTTP parts of the code and use DefaultHttpServerConnection.
 * Add proper encoding and responses.
 * Review how Rhino context is created and recreated on error.
 * Rewrite this code in JavaScript and compile with Rhino!
 */
public class RhinoDroidServer extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Create evaluator here in the UI thread, because it will be called in this thread.
        new Thread(new Server(this, new Evaluator(this))).start();
    }
    
    void print(String s)
    {
        //System.out.println(s);
        Log.i("JSServer", s);
    }
    
    class Evaluator
    {
        Object theActivity;
        Context cx;
        Scriptable scope;
        
        public Evaluator(Object activity)
        {
            theActivity = activity;
            
            // Creates and enters a Context. The Context stores information
            // about the execution environment of a script.
            // It is important that Context.evaluateString is called in the
            // same thread as the context was created in.
            cx = Context.enter();
            cx.setOptimizationLevel(-1);
            
            // Initialize the standard objects (Object, Function, etc.)
            // This must be done before scripts can be executed. Returns
            // a scope object that we use in later calls.
            scope = cx.initStandardObjects();
            ScriptableObject.putProperty(
                scope, "TheActivity", Context.javaToJS(theActivity, scope));
        }
        
        public void exit()
        {
            Context.exit();
        }
        
        public Object evalInUiThread(final String code)
        {
            final AtomicReference<Object> result = new AtomicReference<Object>(null);
            
            ((Activity) theActivity).runOnUiThread(new Runnable() 
            {
                public void run() 
                {
                    try 
                    {
                        //cx = ContextFactory.getGlobal().enterContext(cx);
                        Object x = eval(code);
                        result.set(x);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        result.set(e);
                    }
                }
            });
            
            while (null == result.get()) ;
            
            return result.get();
        }

        public Object eval(final String code)
        {
            try 
            {
                //ContextFactory.enterContext(cx);
                return cx.evaluateString(scope, code, "eval:", 1, null);
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
                return e;
            }
        }
    }
    
    /**
     * Close your eyes, this is not a pretty sight!
     * TODO: Rewrite this code and make it more stable and HTTP compliant.
     */
    class HttpRequest
    {
        BufferedReader reader;
        int contentLength;
        
        public HttpRequest(InputStream in)
        {
            reader = new BufferedReader(new InputStreamReader(in));
        }
        
        public void close()
        {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        public String readHeader()
        {
            try
            {
                StringBuffer header = new StringBuffer();
                
                while (true)
                {
                    String line = reader.readLine();
                    if (null != line)
                    {
                        // When we get an empty line we have read the header.
                        if (0 == line.length()) { break; }
                        header.append(line + "\n");
                    }
                }
                return header.toString();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }

        public void parseHeader()
        {
            try
            {
                while (true)
                {
                    String line = reader.readLine();
                    if (null != line)
                    {
                        // When we get an empty line we have read the header.
                        if (0 == line.length()) { break; }
                        if (line.startsWith("Content-length: "))
                        {
                            String data = line.substring(line.indexOf(": ") + 2);
                            contentLength = Integer.parseInt(data);
                            print("Content-length = " + contentLength);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        public String readContents()
        {
            try
            {
                StringBuffer contents = new StringBuffer();
                int totalRead = 0;
                while (true)
                { 
                    if (totalRead >= contentLength) { break; }
                
                    String line = reader.readLine();
                    if (null != line)
                    {
                        // When we get an empty line we have read the header.
                        if (0 == line.length()) { break; }
                        contents.append(line + "\n");
                        totalRead = totalRead + line.length() + 1;
                    }
                }
                return contents.toString();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return null;
            }
        }
        
//        public void foo(Socket socket)
//        {
//            DefaultHttpServerConnection connection = new DefaultHttpServerConnection();
//            HttpParams params = new BasicHttpParams();
//            try {
//                connection.bind(socket, params);
//                HttpRequest header = connection.receiveRequestHeader(); 
//                BasicHttpEntityEnclosingRequest request = 
//                    new  BasicHttpEntityEnclosingRequest(header.getRequestLine()); 
//                connection.receiveRequestEntity(request);
//                HttpEntity entity = request.getEntity();
//            } catch (IOException e) {
//                e.printStackTrace();
//            } catch (HttpException e) {
//                e.printStackTrace();
//            }
//        }
        
    }
    
    class StreamWriter
    {
        public void write(OutputStream out, String data)
        {
            try
            {
                DataOutputStream stream = new DataOutputStream(out);
                stream.writeChars(data);
                stream.flush();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
    
    class Server implements Runnable
    {
        Object theActivity;
        Evaluator theEvaluator;
        
        public Server(Object activity, Evaluator evaluator)
        {
            theActivity = activity;
            theEvaluator = evaluator;
        }
        
        public void run()
        {
            boolean shutdown = false;
            while (!shutdown)
            {
                print("Welcome to JavaScript on Android!");
                try  
                {
                    //Evaluator theEvaluator = new Evaluator(theActivity);
                    ServerSocket serversocket = new ServerSocket(4042);
                    
                    boolean restart = false;
                    while (!restart) 
                    {
                        print("Waiting for connection...");
                        Socket socket = serversocket.accept();
                        print("Connected!");
                        
                        // Read from socket
                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream();
                        HttpRequest request = new HttpRequest(in);
                        request.parseHeader();
                        String data = request.readContents();
                        
                        print("data: " + data);
                        
                        if (data.startsWith("restart"))
                        {
                            restart = true;
                            socket.close();
                            break;
                        }
                        
                        if (data.startsWith("shutdown"))
                        {
                            restart = true;
                            shutdown = true;
                            socket.close();
                            break;
                        }
                        
                        // Evaluate
                        Object result = theEvaluator.evalInUiThread(data);
                        // Object result = new String("Android here!");
                        
                        // Send reply.
                        PrintStream output = new PrintStream(out);
                        output.print(result.toString());
                        output.close();
                        request.close();
                        socket.close();
                    } // while
                    
                    serversocket.close();
                    theEvaluator.exit();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }  // while
        } // run
    } // class Server
}
