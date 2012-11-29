void say(String msg, String title) {
  final AlertDialog.Builder builder=new AlertDialog.Builder(this);
  builder.setTitle(title);
  builder.setMessage(msg);
  builder.setIcon(android.R.drawable.ic_dialog_alert);
  builder.setPositiveButton("OK", void() { });
  builder.show();
}