package com.blogspot.khingblue.digidonate;

import com.blogspot.khingblue.util.IabBroadcastReceiver;
import com.blogspot.khingblue.util.IabBroadcastReceiver.IabBroadcastListener;
import com.blogspot.khingblue.util.IabHelper;
import com.blogspot.khingblue.util.IabHelper.IabAsyncInProgressException;
import com.blogspot.khingblue.util.IabResult;
import com.blogspot.khingblue.util.Inventory;
import com.blogspot.khingblue.util.Purchase;

import android.app.Activity;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class DigiDonate extends Activity implements IabBroadcastListener {
    static final String TAG = "DigiDonate";
    static final String SKU_1 = "1_dollar";
    static final String SKU_10 = "10_dollar";
    static final String SKU_30 = "30_dollar";
    // (arbitrary) request code for the purchase flow
    static final int RC_REQUEST = 10001;

    IabHelper mHelper;
    IabBroadcastReceiver mBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_digi_donate);

        // set up buttons
        Button b1 = (Button) findViewById(R.id.btn1);
        Button b10 = (Button) findViewById(R.id.btn10);
        Button b30 = (Button) findViewById(R.id.btn30);

        b1.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                append("\nTrying to donate $1 ... ");
                donate(SKU_1);
            }
        });
        b10.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                append("\nTrying to donate $10 ... ");
                donate(SKU_10);
            }
        });
        b30.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                append("\nTrying to donate $30 ... ");
                donate(SKU_30);
            }
        });

        // set up iap
        String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtnrOA7tldYMOpKZNsGk4apcW0EXLgA49Gmlg7WvmwnRL8gfeLQDsD9NLAx59dw4lwZzurOrqPUJFxQxiRI1zEXcDehmRbeMzLrTBIoWg8Cs71/5ylVICfmRqgmFzVgD3FNYbyU50rX6kXfNiI+sb5jOGCCZs6lA66fN6aGngs2sVsoDE0L31akJZNDsBinecCSKQFadgeMgqAW4R4fgmpXbbTpzSfcTMbcC7BLajS+zUcZ8UeJ3h4H7OfoCIBBUzyvzeziyPmd921MM+8xsiXnH7uTkRIATbAy6cUkuCvakuLLarHLgfYI1fzpviNw3XAeF5ChgY/a+o2m/2KBwkIwIDAQAB";
        Log.d(TAG, "Creating IAB helper.");
        mHelper = new IabHelper(this, base64EncodedPublicKey);
        //mHelper.enableDebugLogging(false);
        Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                Log.d(TAG, "Setup finished.");
                if (!result.isSuccess()) {
                    Log.e(TAG, "Problem setting up in-app billing: " + result);
                    return;
                }

                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;

                mBroadcastReceiver = new IabBroadcastReceiver(DigiDonate.this);
                IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
                registerReceiver(mBroadcastReceiver, broadcastFilter);

                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                Log.d(TAG, "Setup successful. Querying inventory.");
                try {
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                } catch (IabAsyncInProgressException e) {
                    Log.e(TAG, "Error querying inventory. Another async operation in progress.");
                }
            }
        });
    }

    private void append(String s) {
        TextView textView = (TextView)findViewById(R.id.textView);
        if (textView == null)
            return;
        textView.append(s);
        Log.d(TAG, s);
    }

    private void donate(String sku) {
        String payload = ""; // This can give back when the callback returns
        try {
            Log.d(TAG, "launchPurchaseFlow...");
            mHelper.launchPurchaseFlow(this, sku, RC_REQUEST, mPurchaseFinishedListener, payload);
            Log.d(TAG, "launchPurchaseFlow...done");
        } catch (IabAsyncInProgressException e) {
            append("Error launching purchase flow. Another async operation in progress.");
        }
    }

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            append("Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure()) {
                append("Error purchasing: " + result);
                return;
            }
            append("success.");

            if (purchase.getSku().equals(SKU_1)
                || purchase.getSku().equals(SKU_10)
                || purchase.getSku().equals(SKU_30)) {
                consume(purchase);
            }
        }
    };

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                Log.e(TAG, "Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");
            consume(inventory.getPurchase(SKU_1));
            consume(inventory.getPurchase(SKU_10));
            consume(inventory.getPurchase(SKU_30));
        }
    };

    private void consume(Purchase purchase) {
        if (purchase != null) {
            Log.d(TAG, "Consuming it so that we can purchase it again.");
            try {
                mHelper.consumeAsync(purchase, mConsumeFinishedListener);
            } catch (IabAsyncInProgressException e) {
                append("Error consuming sku. Another async operation in progress.");
            }
        }
    }

    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        Log.d(TAG, "Received broadcast notification. Querying inventory.");
        try {
            mHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabAsyncInProgressException e) {
            Log.e(TAG, "Error querying inventory. Another async operation in progress.");
        }
    }

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);
        }
    };
}
