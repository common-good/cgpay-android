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

package org.rcredits.zxing.client.android.history;

import org.rcredits.zxing.client.android.CaptureActivity;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import org.rcredits.zxing.client.android.Intents;
import org.rcredits.pos.R;

import java.util.List;

public final class HistoryActivity extends ListActivity {

  private static final String TAG = HistoryActivity.class.getSimpleName();

  private HistoryManager historyManager;
  private HistoryItemAdapter adapter;
  
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.historyManager = new HistoryManager(this);  
    adapter = new HistoryItemAdapter(this);
    setListAdapter(adapter);
    ListView listview = getListView();
    registerForContextMenu(listview);
  }

  @Override
  protected void onResume() {
    super.onResume();
    reloadHistoryItems();
  }

  private void reloadHistoryItems() {
    List<HistoryItem> items = historyManager.buildHistoryItems();
    adapter.clear();
    for (HistoryItem item : items) {
      adapter.add(item);
    }
    if (adapter.isEmpty()) {
      adapter.add(new HistoryItem(null, null, null));
    }
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    if (adapter.getItem(position).getResult() != null) {
      Intent intent = new Intent(this, CaptureActivity.class);
      intent.putExtra(Intents.History.ITEM_NUMBER, position);
      setResult(Activity.RESULT_OK, intent);
      finish();
    }
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu,
                                  View v,
                                  ContextMenu.ContextMenuInfo menuInfo) {
    int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
    if (position >= adapter.getCount() || adapter.getItem(position).getResult() != null) {
      menu.add(Menu.NONE, position, position, R.string.history_clear_one_history_text);
    } // else it's just that dummy "Empty" message
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    int position = item.getItemId();
    historyManager.deleteHistoryItem(position);
    reloadHistoryItems();
    return true;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (historyManager.hasHistoryItems()) {
      MenuInflater menuInflater = getMenuInflater();

    }
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    return true;
  }

}
