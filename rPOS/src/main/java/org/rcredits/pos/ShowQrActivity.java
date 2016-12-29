/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rcredits.pos;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.rcredits.pos.A;
import org.rcredits.pos.Act;
import org.rcredits.pos.Identify;
import org.rcredits.pos.Q;
import org.rcredits.pos.R;

/**
 * Display the device owner's rCard photo and QR code
 * @author William Spademan 10/9/2016
 */
public final class ShowQrActivity extends Act {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_showqr);

        String company = A.b.get("company");
        boolean hideCompany = (company == null || company.equals(A.agentName));
        act.setView(R.id.customer_company, company).setVisibility(hideCompany ? View.GONE : View.VISIBLE);
        act.setView(R.id.customer_name, A.agentName);
        act.setView(R.id.customer_qid, A.agent);

        ImageView photo = (ImageView) findViewById(R.id.photo);
        Q q = A.b.db.oldCustomer(A.agent);
        photo.setImageBitmap(Identify.scaledPhoto(q.getBlob("photo")));
        String s = q.getString(DbSetup.AGT_ABBREV); // get abbreviated QR string (region/acct. or fmtregionacct)
        if (s.indexOf("/") == -1) { // new format
            String counter = A.getStored("counter");
            int n = A.empty(counter) ? 0 : (A.n(counter).intValue() + 1);
            A.setStored("counter", n + "");
            s += q.getString("code") + (A.b.test ? "." : "-") + rCard.base26AZ(n);
        } else s = "//" + s.replace("/", A.b.test ? ".RC4.ME/" : ".RC2.ME/") + q.getString("code");
        q.close();

        ImageView qr = (ImageView) findViewById(R.id.qr);
        try {
            qr.setImageBitmap(encodeAsBitmap(s));
        } catch (WriterException e) {}
    }

    /**
     * Return a QR code image for the given string.
     * @param str
     * @return QR bitmap
     * @throws WriterException
     */
    Bitmap encodeAsBitmap(String str) throws WriterException {
        Display display = getWindowManager().getDefaultDisplay();
        int qrSize = display.getWidth(); // QR code should nearly fill the screen width

        BitMatrix result = new QRCodeWriter().encode(str, BarcodeFormat.QR_CODE, qrSize, qrSize, null);

        int w = result.getWidth();
        int h = result.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, qrSize, 0, 0, w, h);
        return bitmap;
    }

}
