// adb push Hello.js /data/data/miki.rhinodroid/files/
var TextView = Packages.android.widget.TextView;
var view = new TextView(TheActivity);
var text = 'Hello Android!\nThis is JavaScript in action!';
view.setText(text);
TheContentView.removeAllViews();
TheContentView.addView(view);