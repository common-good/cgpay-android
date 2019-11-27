/*
 * Copyright 2012 ZXing authors
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

package org.cg.zxing.client.android.history;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import earth.commongood.cgpay.R;

import java.util.ArrayList;

final class HistoryItemAdapter extends ArrayAdapter<HistoryItem> {

  private final Activity activity;

  HistoryItemAdapter(Activity activity) {
    super(activity, 0, new ArrayList<HistoryItem>());
    this.activity = activity;
  }

  @Override
  public View getView(int position, View view, ViewGroup viewGroup) {
    return view;
  }

}
