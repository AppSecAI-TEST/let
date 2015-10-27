/*
 * Copyright (C) 2015 Can Elmas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.canelmas.let;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by can on 07/10/15.
 */
public final class RuntimePermissionRequest {

    private static final AtomicInteger PERMISSIONS_REQUEST_CODE = new AtomicInteger();

    final Activity source;
    final ProceedingJoinPoint joinPoint;

    public RuntimePermissionRequest(ProceedingJoinPoint joinPoint, Activity source) {
        this.source = source;
        this.joinPoint = joinPoint;
    }

    public void retry() {
        proceed(true);
    }

    protected Object proceed() {
        return proceed(false);
    }

    private Object proceed(final boolean skipRationale) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        final String[] permissionList = signature.getMethod().getAnnotation(AskPermission.class).value();

        Logger.log(">>> " + signature.getName() + "() requires " + permissionList.length + " permission");
        /**
         *  Permissions to ask and to show rationales
         */
        final List<String> permissionsToAsk = new ArrayList<>();
        final List<String> permissionsToExplain = new ArrayList<>();

        for (String permission : permissionList) {

            Logger.log("\t" + permission);

            // check if permission name is valid
            if (!isPermissionValid(permission)) {
                throw new LetException("Permission not valid!");
            } else {

                final int permissionResult = ActivityCompat.checkSelfPermission(source, permission);

                Logger.log("\t\talreadyGranted=" + String.valueOf(permissionResult != -1));

                if (permissionResult == PackageManager.PERMISSION_DENIED) {

                    final boolean shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(source, permission);
                    Logger.log("\t\tshowRationale=" + shouldShowRationale);

                    if (shouldShowRationale && !skipRationale) {

                        permissionsToExplain.add(permission);

                    } else {

                        permissionsToAsk.add(permission);

                    }

                }

            }

        }

        /**
         *  -Show rationales if necessary and return, before making any permission request
         *  -Trigger necessary permission request and return
         *  -Permissions needed already granted, no need to make request; proceed with the actual method
         */
        RuntimePermissionListener listener = RuntimePermissionListener.class.isInstance(source) ?
                (RuntimePermissionListener) source : null;

        if (!permissionsToExplain.isEmpty()) {

            Logger.log("<<< Should show Rationale");
            if (null != listener)
                listener.onShowPermissionRationale(permissionsToExplain, new RuntimePermissionRequest(joinPoint, source));
            else {
                throw new LetException(source + " should implement RuntimePermissionListener");
            }
            return null;

        } else if (!permissionsToAsk.isEmpty()) {

            final int requestCode = PERMISSIONS_REQUEST_CODE.getAndIncrement();
            Logger.log("<<< Making permission request");

            DelayedTasks.add(new DelayedTasks.Task(permissionsToAsk, requestCode, joinPoint));

            ActivityCompat.requestPermissions(source, permissionsToAsk.toArray(new String[]{}), requestCode);

        } else {

            Logger.log("<<< Permissions granted");
            try {
                return joinPoint.proceed();
            } catch (Throwable t) {
                throw new LetException("Proceeding with the origin method failed!", t);
            }

        }

        //  actual method not called
        return null;
    }

    private boolean isPermissionValid(final String permissionName) {
        // TODO
        return !TextUtils.isEmpty(permissionName);
    }

}
