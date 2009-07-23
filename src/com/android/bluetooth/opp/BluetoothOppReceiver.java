/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import com.android.bluetooth.R;

import android.app.NotificationManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothError;
import android.bluetooth.BluetoothIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

/**
 * Receives and handles: system broadcasts; Intents from other applications;
 * Intents from OppService; Intents from modules in Opp application layer.
 */
public class BluetoothOppReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothOppReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {

            context.startService(new Intent(context, BluetoothOppService.class));
        } else if (action.equals(BluetoothIntent.BLUETOOTH_STATE_CHANGED_ACTION)) {
            if (BluetoothDevice.BLUETOOTH_STATE_ON == intent.getIntExtra(
                    BluetoothIntent.BLUETOOTH_STATE, BluetoothError.ERROR)) {
                if (Constants.LOGVV) {
                    Log.v(TAG, "Received BLUETOOTH_STATE_CHANGED_ACTION, BLUETOOTH_STATE_ON");
                }
                context.startService(new Intent(context, BluetoothOppService.class));

                // If this is within a sending process, continue the handle
                // logic to display device picker dialog.
                synchronized (this) {
                    if (BluetoothOppManager.getInstance(context).mSendingFlag) {
                        // reset the flags
                        BluetoothOppManager.getInstance(context).mSendingFlag = false;

                        Intent in1 = new Intent(context, BluetoothDevicePickerActivity.class);
                        in1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(in1);
                    }
                }
            }
        } else if (action.equals(BluetoothShare.BLUETOOTH_DEVICE_SELECTED_ACTION)) {
            BluetoothOppManager mOppManager = BluetoothOppManager.getInstance(context);

            String btAddr = (String)intent.getStringExtra("BT_ADDRESS");

            if (Constants.LOGVV) {
                Log.v(TAG, "Received BT device selected intent, bt addr: " + btAddr);
            }

            // Insert transfer session record to database
            mOppManager.startTransfer(btAddr);

            // Display toast message
            String deviceName = mOppManager.getDeviceName(btAddr);
            String toastMsg;
            if (mOppManager.mMultipleFlag) {
                toastMsg = context.getString(R.string.bt_toast_5).replace("%s1",
                        Integer.toString(mOppManager.mfileNumInBatch)).replace("%s2", deviceName);
            } else {
                toastMsg = context.getString(R.string.bt_toast_4).replace("%s", deviceName);
            }
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
        } else if (action.equals(Constants.ACTION_INCOMING_FILE_CONFIRM)) {
            if (Constants.LOGVV) {
                Log.v(TAG, "Receiver ACTION_INCOMING_FILE_CONFIRM");
            }

            Uri uri = intent.getData();
            Intent in = new Intent(context, BluetoothOppIncomingFileConfirmActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.setData(uri);
            context.startActivity(in);

            NotificationManager notMgr = (NotificationManager)context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            if (notMgr != null) {
                notMgr.cancel((int)ContentUris.parseId(intent.getData()));
                Log.v(TAG, "notMgr.cancel called");
            }
        } else if (action.equals(BluetoothShare.INCOMING_FILE_CONFIRMATION_REQUEST_ACTION)) {
            if (Constants.LOGVV) {
                Log.v(TAG, "Receiver INCOMING_FILE_NOTIFICATION");
            }

            Toast.makeText(context, context.getString(R.string.incoming_file_toast_msg),
                    Toast.LENGTH_SHORT).show();

        } else if (action.equals(Constants.ACTION_OPEN) || action.equals(Constants.ACTION_LIST)) {
            if (Constants.LOGVV) {
                if (action.equals(Constants.ACTION_OPEN)) {
                    Log.v(TAG, "Receiver open for " + intent.getData());
                } else {
                    Log.v(TAG, "Receiver list for " + intent.getData());
                }
            }

            BluetoothOppTransferInfo transInfo = new BluetoothOppTransferInfo();
            Uri uri = intent.getData();
            transInfo = BluetoothOppUtility.queryRecord(context, uri);
            if (transInfo == null) {
                if (Constants.LOGVV) {
                    Log.e(TAG, "Error: Can not get data from db");
                }
                return;
            }

            if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND
                    && BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                // if received file successfully, open this file
                BluetoothOppUtility.openReceivedFile(context, transInfo.mFileName,
                        transInfo.mFileType, transInfo.mTimeStamp);
                BluetoothOppUtility.updateVisibilityToHidden(context, uri);
            } else {
                Intent in = new Intent(context, BluetoothOppTransferActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                in.setData(uri);
                context.startActivity(in);
            }

            NotificationManager notMgr = (NotificationManager)context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            if (notMgr != null) {
                notMgr.cancel((int)ContentUris.parseId(intent.getData()));
                Log.v(TAG, "notMgr.cancel called");
            }
        } else if (action.equals(Constants.ACTION_HIDE)) {
            if (Constants.LOGVV) {
                Log.v(TAG, "Receiver hide for " + intent.getData());
            }
            Cursor cursor = context.getContentResolver().query(intent.getData(), null, null, null,
                    null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int statusColumn = cursor.getColumnIndexOrThrow(BluetoothShare.STATUS);
                    int status = cursor.getInt(statusColumn);
                    int visibilityColumn = cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY);
                    int visibility = cursor.getInt(visibilityColumn);
                    int userConfirmationColumn = cursor
                            .getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION);
                    int userConfirmation = cursor.getInt(userConfirmationColumn);
                    if ((BluetoothShare.isStatusCompleted(status) || (userConfirmation == BluetoothShare.USER_CONFIRMATION_PENDING))
                            && visibility == BluetoothShare.VISIBILITY_VISIBLE) {
                        ContentValues values = new ContentValues();
                        values.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
                        context.getContentResolver().update(intent.getData(), values, null, null);
                        if (Constants.LOGVV) {
                            Log.v(TAG, "Action_hide received and db updated");
                        }
                    }
                }
                cursor.close();
            }
        } else if (action.equals(BluetoothShare.TRANSFER_COMPLETED_ACTION)) {
            if (Constants.LOGVV) {
                Log.v(TAG, "Receiver Transfer Complete Intent for " + intent.getData());
            }

            String toastMsg = null;
            BluetoothOppTransferInfo transInfo = new BluetoothOppTransferInfo();
            transInfo = BluetoothOppUtility.queryRecord(context, intent.getData());
            if (transInfo == null) {
                if (Constants.LOGVV) {
                    Log.e(TAG, "Error: Can not get data from db");
                }
                return;
            }

            if (BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                if (transInfo.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    toastMsg = context.getString(R.string.notification_sent).replace("%s",
                            transInfo.mFileName);
                } else if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                    toastMsg = context.getString(R.string.notification_received).replace("%s",
                            transInfo.mFileName);
                }

            } else if (BluetoothShare.isStatusError(transInfo.mStatus)) {
                if (transInfo.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    toastMsg = context.getString(R.string.notification_sent_fail).replace("%s",
                            transInfo.mFileName);
                } else if (transInfo.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                    toastMsg = context.getString(R.string.download_fail_line1);
                }
            }
            if (Constants.LOGVV) {
                Log.v(TAG, "Toast msg == " + toastMsg);
            }
            if (toastMsg != null) {
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
            }
        }
    }
}