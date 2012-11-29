package org.rcredits;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.barcodescanner.CaptureActivity;
import org.rcredits.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class Util {

  public static void showMessageWithOk(final Context mContext, final String message) {
    ((Activity) mContext).runOnUiThread(new Runnable() {
      
      public void run() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(mContext);
        alert.setTitle(R.string.app_name);

        alert.setMessage(message);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {

          }
        });
          alert.show();
      }
    });
  }
  
  public static void showCallBackMessage(final Context mContext, final String message, final CaptureActivity callBack) {
    ((Activity) mContext).runOnUiThread(new Runnable() {
      
      public void run() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(mContext);
        alert.setTitle(R.string.app_name);
        alert.setCancelable(false);
        alert.setMessage(message);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
            callBack.onBackClick();
          }
        });
          alert.show();
      }
    });
  }
  
  // for below Android 2.2
  public static boolean isValidEmailAddress(String email){
    final String regex = "[a-zA-Z0-9+._%-+]{1,256}@[a-zA-Z0-9][a-zA-Z0-9-]{0,64}" +
             "(.[a-zA-Z0-9][a-zA-Z0-9-]{0,25})";
    final Pattern emailPattern = Pattern.compile(regex);
    final Matcher emailMatcher = emailPattern.matcher(email);
        return emailMatcher.matches();
  }
  
//   // for above Android 2.2 
//   public static boolean isEmailValid(CharSequence email) {
//        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
//   }
  
  public static String convertDate(String preFormatDate){
    String[] array = preFormatDate.split("-");
    int mYear = Integer.valueOf(array[0]);
    int mMonth = Integer.valueOf(array[1]);
    int mDay = Integer.valueOf(array[2]);
    
    Date dt = new Date(mYear-1900,mMonth,mDay);
    String strDate= DateFormat.getDateInstance(DateFormat.MEDIUM).format(dt);
    return strDate;
  }
  
  public static String convert24hrTo12hr(String time){
    String am = "AM";
    String pm = "PM";
    String[] array = time.split(":");
    int hour = Integer.valueOf(array[0]);
    int minute = Integer.valueOf(array[1]);
//     int second = Integer.valueOf(array[2]);
    String tempMinute = minute >9?String.valueOf(minute):"0"+minute;
//     String tempSecond = second >9?String.valueOf(second):"0"+second;
    String newTime = "";
    if(hour == 12){
      newTime = hour+":"+tempMinute+" "+pm;
    }else if(hour>12){
      int tempHour = hour % 12;
      newTime = tempHour+":"+tempMinute+" "+pm;
    }else{
      newTime = hour+":"+tempMinute+" "+am;
    }
    return newTime;
  }
  
  public static boolean isCurrentDate(String preFormatDate){
    String[] array = preFormatDate.split("-");
    int mYear = Integer.valueOf(array[0]);
    int mMonth = Integer.valueOf(array[1])-1;
    int mDay = Integer.valueOf(array[2]);
    
    Date preDate = new Date(mYear-1900,mMonth,mDay);
    Date currentDate = new Date();
    int flag = -1; // currentDate.compareTo(preDate);
    if(preDate.getYear() == currentDate.getYear() && 
        preDate.getMonth() == currentDate.getMonth() &&
        preDate.getDate() == currentDate.getDate()){
      
      flag = 0;
    }
    return flag == 0?true:false;
  }
  
    public static String now() {
         String DATE_FORMAT_NOW = "HH:mm:ss";
      Calendar cal = Calendar.getInstance();
    SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
    return sdf.format(cal.getTime());
    }
  
  public static boolean isCurrentTime(String preFormatStartTime, String preFormatStopTime){
    boolean flag = false;
    String currentTime = now();
    String pattern = "HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        try {
            Date date1 = sdf.parse(currentTime);
            Date date2 = sdf.parse(preFormatStartTime);
            Date date3 = sdf.parse(preFormatStopTime);
            if(date2.compareTo(date1)<0 && date3.compareTo(date1)>0){
          flag = true;
        }
        } catch (ParseException e){
          e.printStackTrace();
        }
    return flag;
  }
  
  public static boolean isEventInProgress(String preFormatDate, String preFormatStartTime, String preFormatStopTime){
    boolean flag = false;
    if(isCurrentDate(preFormatDate)){
      flag = isCurrentTime(preFormatStartTime, preFormatStopTime);
    }
    return flag;
  }
  
  public static String convertDateToMMDDYYYY(String preFormatDate){
    String[] array = preFormatDate.split("-");
    int mYear = Integer.valueOf(array[0]);
    int mMonth = Integer.valueOf(array[1]);
    int mDay = Integer.valueOf(array[2]);
    
    return (mMonth<10?("0"+mMonth):mMonth)+"/"+
         (mDay<10?("0"+mDay):mDay)+"/"+
         mYear;
  }
  
  public static String convertDateToYYYYMMDD(String preFormatDate){
    String[] array = preFormatDate.split("/");
    int mYear = Integer.valueOf(array[2]);
    int mMonth = Integer.valueOf(array[0]);
    int mDay = Integer.valueOf(array[1]);
    
    String strDate = mYear+"-"+mMonth+"-"+mDay;
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    Date dateStr = null;
    try {
      dateStr = formatter.parse(strDate);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    String formattedDate = formatter.format(dateStr);
    return formattedDate;
  }
  
}
