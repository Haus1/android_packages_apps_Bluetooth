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
import com.google.android.collect.Lists;

import android.util.Log;
import android.net.Uri;
import android.content.ContentValues;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This class has some utilities for Opp application;
 */
public class BluetoothOppUtility {
    private static final String TAG = "BluetoothOppUtility";

    public static BluetoothOppTransferInfo queryRecord(Context context, Uri uri) {
        BluetoothOppTransferInfo info = new BluetoothOppTransferInfo();
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                info.mID = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare._ID));
                info.mStatus = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
                info.mDirection = cursor.getInt(cursor
                        .getColumnIndexOrThrow(BluetoothShare.DIRECTION));
                info.mTotalBytes = cursor.getInt(cursor
                        .getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
                info.mCurrentBytes = cursor.getInt(cursor
                        .getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
                info.mTimeStamp = cursor.getLong(cursor
                        .getColumnIndexOrThrow(BluetoothShare.TIMESTAMP));
                info.mDestAddr = cursor.getString(cursor
                        .getColumnIndexOrThrow(BluetoothShare.DESTINATION));

                info.mFileName = cursor.getString(cursor
                        .getColumnIndexOrThrow(BluetoothShare._DATA));
                if (info.mFileName == null) {
                    info.mFileName = cursor.getString(cursor
                            .getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
                }
                if (info.mFileName == null) {
                    info.mFileName = context.getString(R.string.unknown_file);
                }

                info.mFileUri = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.URI));

                if (info.mFileUri != null) {
                    Uri u = Uri.parse(info.mFileUri);
                    info.mFileType = context.getContentResolver().getType(u);
                } else {
                    Uri u = Uri.parse(info.mFileName);
                    info.mFileType = context.getContentResolver().getType(u);
                }
                if (info.mFileType == null) {
                    info.mFileType = cursor.getString(cursor
                            .getColumnIndexOrThrow(BluetoothShare.MIMETYPE));
                }

                info.mDeviceName = BluetoothOppManager.getInstance(context).getDeviceName(
                        info.mDestAddr);

                if (Constants.LOGVV) {
                    Log.v(TAG, "Get data from db:" + info.mFileName + info.mFileType
                            + info.mDestAddr);
                }
            }
            cursor.close();
        } else {
            info = null;
            if (Constants.LOGVV) {
                Log.v(TAG, "BluetoothOppManager Error: not got data from db for uri:" + uri);
            }
        }
        return info;
    }

    /**
     * Organize Array list for transfers in one batch
     */
    public static ArrayList<String> queryTransfersInBatch(Context context, Long timeStamp) {
        ArrayList<String> uris = Lists.newArrayList();
        final String WHERE = BluetoothShare.TIMESTAMP + " == " + timeStamp;

        Cursor metadataCursor = context.getContentResolver().query(BluetoothShare.CONTENT_URI,
                new String[] {
                    BluetoothShare._DATA
                }, WHERE, null, BluetoothShare._ID);

        if (metadataCursor == null) {
            return null;
        }

        for (metadataCursor.moveToFirst(); !metadataCursor.isAfterLast(); metadataCursor
                .moveToNext()) {
            String fileName = metadataCursor.getString(0);
            Uri path = Uri.parse(fileName);
            // If there is no scheme, then it must be a file
            if (path.getScheme() == null) {
                path = Uri.fromFile(new File(fileName));
            }
            uris.add(path.toString());
            if (Constants.LOGVV) {
                Log.d(TAG, "Uri in this batch: " + path.toString());
            }
        }
        metadataCursor.close();
        return uris;
    }

    /**
     * Open the received file with appropriate application, if can not find
     * application to handle, display error dialog.
     */
    public static void openReceivedFile(Context context, String fileName, String mimetype,
            Long timeStamp) {
        if (fileName == null || mimetype == null) {
            Log.e(TAG, "ERROR: Para fileName ==null, or mimetype == null");
            return;
        }

        File f = new File(fileName);
        if (!f.exists()) {
            Intent in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("title", context.getString(R.string.not_exist_file));
            in.putExtra("content", context.getString(R.string.not_exist_file_desc));
            context.startActivity(in);
            return;
        }

        Uri path = Uri.parse(fileName);
        // If there is no scheme, then it must be a file
        if (path.getScheme() == null) {
            path = Uri.fromFile(new File(fileName));
        }

        if (isRecognizedFileType(context, path, mimetype)) {
            Intent activityIntent = new Intent(Intent.ACTION_VIEW);
            activityIntent.setDataAndType(path, mimetype);

            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (Constants.LOGVV) {
                    Log.d(TAG, "ACTION_VIEW intent sent out: " + path + " / " + mimetype);
                }
                context.startActivity(activityIntent);
            } catch (ActivityNotFoundException ex) {
                if (Constants.LOGVV) {
                    Log.d(TAG, "no activity for handling ACTION_VIEW intent:  " + mimetype, ex);
                }
            }
        } else {
            Intent in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            in.putExtra("title", context.getString(R.string.unknown_file));
            in.putExtra("content", context.getString(R.string.unknown_file_desc));
            context.startActivity(in);
        }
    }

    /**
     * To judge if the file type supported (can be handled by some app) by phone
     * system.
     */
    public static boolean isRecognizedFileType(Context context, Uri fileUri, String mimetype) {
        boolean ret = true;

        if (Constants.LOGV) {
            Log.v(TAG, "RecognizedFileType() fileUri: " + fileUri + " mimetype: " + mimetype);
        }

        Intent mimetypeIntent = new Intent(Intent.ACTION_VIEW);
        mimetypeIntent.setDataAndType(fileUri, mimetype);
        List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(mimetypeIntent,
                PackageManager.MATCH_DEFAULT_ONLY);

        if (list.size() == 0) {
            if (Constants.LOGV) {
                Log.v(TAG, "NO application to handle MIME type " + mimetype);
            }
            ret = false;
        }
        return ret;
    }

    /**
     * update visibility to Hidden
     */
    public static void updateVisibilityToHidden(Context context, Uri uri) {
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_HIDDEN);
        context.getContentResolver().update(uri, updateValues, null, null);
    }

    /**
     * Helper function to build the progress text.
     */
    public static String formatProgressText(long totalBytes, long currentBytes) {
        if (totalBytes <= 0) {
            return "0%";
        }
        long progress = currentBytes * 100 / totalBytes;
        StringBuilder sb = new StringBuilder();
        sb.append(progress);
        sb.append('%');
        return sb.toString();
    }

    /**
     * Get status description according to status code.
     */
    public static String getStatusDescription(Context context, int statusCode) {
        String ret;
        if (statusCode == BluetoothShare.STATUS_PENDING) {
            ret = context.getString(R.string.status_pending);
        } else if (statusCode == BluetoothShare.STATUS_RUNNING) {
            ret = context.getString(R.string.status_running);
        } else if (statusCode == BluetoothShare.STATUS_SUCCESS) {
            ret = context.getString(R.string.status_success);
        } else if (statusCode == BluetoothShare.STATUS_NOT_ACCEPTABLE) {
            ret = context.getString(R.string.status_not_accept);
        } else if (statusCode == BluetoothShare.STATUS_FORBIDDEN) {
            ret = context.getString(R.string.status_forbidden);
        } else if (statusCode == BluetoothShare.STATUS_CANCELED) {
            ret = context.getString(R.string.status_canceled);
        } else if (statusCode == BluetoothShare.STATUS_FILE_ERROR) {
            ret = context.getString(R.string.status_file_error);
        } else if (statusCode == BluetoothShare.STATUS_ERROR_NO_SDCARD) {
            ret = context.getString(R.string.status_no_sd_card);
        } else if (statusCode == BluetoothShare.STATUS_CONNECTION_ERROR) {
            ret = context.getString(R.string.status_connection_error);
        } else if (statusCode == BluetoothShare.STATUS_ERROR_SDCARD_FULL) {
            ret = context.getString(R.string.bt_sm_2_1);
        } else if ((statusCode == BluetoothShare.STATUS_BAD_REQUEST)
                || (statusCode == BluetoothShare.STATUS_LENGTH_REQUIRED)
                || (statusCode == BluetoothShare.STATUS_PRECONDITION_FAILED)
                || (statusCode == BluetoothShare.STATUS_UNHANDLED_OBEX_CODE)
                || (statusCode == BluetoothShare.STATUS_OBEX_DATA_ERROR)) {
            ret = context.getString(R.string.status_protocol_error);
        } else {
            ret = context.getString(R.string.status_unknown_error);
        }
        return ret;
    }

    /**
     * Retry the failed transfer: Will insert a new transfer session to db
     */
    public static void retryTransfer(Context context, BluetoothOppTransferInfo transInfo) {
        ContentValues values = new ContentValues();
        values.put(BluetoothShare.URI, transInfo.mFileUri);
        values.put(BluetoothShare.MIMETYPE, transInfo.mFileType);
        values.put(BluetoothShare.DESTINATION, transInfo.mDestAddr);

        final Uri contentUri = context.getContentResolver().insert(BluetoothShare.CONTENT_URI,
                values);
        if (Constants.LOGVV) {
            Log
                    .v(TAG, "Insert contentUri: " + contentUri + "  to device: "
                            + transInfo.mDeviceName);
        }
    }

}