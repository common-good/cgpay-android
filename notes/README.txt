
I've used this code with both Android Studio and with ADT (eclipse).
rCreditsProject is the root of the Android Studio project,
rCreditsProject/rCredits/src/main is the root of the ADT project.

Although this app behaves, as far as I can tell, exactly as specified,
in using it a bit, I found it annoying.  If you decide that you want
one more page in the UI, from which you push a button or something
to get to the QR Code scanner, I'm happy to do that.  ...other tweaks
as well

The ZXing page is here:
http://code.google.com/p/zxing/downloads/list?can=1&q=&colspec=Filename+Summary+Uploaded+ReleaseDate+Size+DownloadCount

The app, as written, depends on having a specific barcode scanner
installed.  I've included two version of that scanner.  The one with
the higher version number is the one to use, if you can.  It requires
Ice Cream Sandwich (API 15).  The one with the lower version number (4.3.2)
will work on version of Android back to Froyo (7?).  I believe that
the app itself, will work back that far as well.

I'm afraid I've left a lot of verification/testing to you.  I don't
have a good way to test on old versions of Android and I have never
actually gotten to an rCredits page from a browser.  I have checked
to see that the browser can start the app, and that the app can start
the browser.  The tag you need looks like this:

<a href="intent:#Intent;action=com.google.zxing.client.android.SCAN;package=org.rcredits;end" />

There is an html page, enclosed, that I used for testing.

The QRCode that you sent me seems to be a non-existent URL.
I tested using the QRCode on the back of an O'Reilly Book
(Programming Android: It's pretty good.  You should pick it up ;-)
and that seems to work as well.

Hope this is useful.  As I say, I'm happy to tweak it if there
are small changes needed.  If this cheap trick won't work --
and it truly is just a cheap trick -- I can see if I can do
something more substantial.
