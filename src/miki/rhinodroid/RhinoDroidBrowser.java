package miki.rhinodroid;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;

import java.net.*;
import java.io.*;

import org.mozilla.javascript.*;
import org.mozilla.javascript.Context;

/**
 * Experimental "browser" that can load and evaluate JavaScript code.
 * Copyright (c) 2009 Mikael Kindborg mikael.kindborg@gmail.com
 * Licence: MIT
 *
 * TODO: 
 * General clean up.
 * Improved user interface.
 * Type in and evaluate JS code in the text field (like a "REPL").
 * Rewrite this code in JavaScript and compile with Rhino!
 */
public class RhinoDroidBrowser extends Activity
{
    LinearLayout mainView;
    ViewGroup contentView;
    EditText navigatorView;
    Evaluator evaluator;
    
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        final EditText navigatorView = new EditText(this);
        navigatorView.setTextSize(20);
        navigatorView.append("Hello JavaScript!");
        contentView = new FrameLayout(this); 
        mainView.addView(navigatorView);
        mainView.addView(contentView);
        TextView textView = new TextView(this);
        textView.setText("Hello");
        textView.setBackgroundColor(Color.rgb(255, 100, 0));
        contentView.addView(textView);
        setContentView(mainView);
        
        navigatorView.setOnEditorActionListener(new TextView.OnEditorActionListener() 
        {
            public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2) {
                if (arg1 == EditorInfo.IME_NULL) {
                    Editable fileOrUrl = navigatorView.getText();
                    if (fileOrUrl.length() == 0) return true;
                    open(fileOrUrl.toString());
                    return true;
                }
                return false;
            }
        });
        
        evaluator = new Evaluator(this, contentView);
    }

    public void open(String fileOrUrl)
    {
        evaluator.eval(read(fileOrUrl));
    }

    public String read(String fileOrUrl)
    {
        try
        {
            InputStream in = null;
            if (fileOrUrl.startsWith("http://"))
            {
                URL url = new URL(fileOrUrl);
                in = url.openStream();
            }
            else
            {
                in = this.openFileInput(fileOrUrl);
            }

            BufferedInputStream bufIn = new BufferedInputStream(in);
            StringBuffer dataBuf = new StringBuffer();
            while (true)
            {
                int data = bufIn.read();
                if (data == -1)
                    break;
                else
                    dataBuf.append((char) data);
            }
            
            bufIn.close();
            in.close();
            
            return dataBuf.toString();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
    
    class Evaluator
    {
        Object theActivity;
        Context cx;
        Scriptable scope;
        
        public Evaluator(Object activity, Object contentView)
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
            ScriptableObject.putProperty(
                scope, "TheContentView", Context.javaToJS(contentView, scope));
        }
        
        public void exit()
        {
            Context.exit();
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
}