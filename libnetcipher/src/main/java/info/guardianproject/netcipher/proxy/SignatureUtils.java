/***
 * Copyright (c) 2014 CommonsWare, LLC
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.guardianproject.netcipher.proxy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.support.annotation.Nullable;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class SignatureUtils {

    private SignatureUtils() {
        // this is a utility class with only static methods
    }

    public static String getOwnSignatureHash(Context context)
            throws
            NameNotFoundException,
            NoSuchAlgorithmException {
        return (getSignatureHash(context, context.getPackageName()));
    }

    public static String getSignatureHash(Context context, String packageName)
            throws
            NameNotFoundException,
            NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Signature sig =
                context.getPackageManager()
                        .getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures[0];

        return (toHexStringWithColons(md.digest(sig.toByteArray())));
    }

    // based on https://stackoverflow.com/a/2197650/115145

    public static String toHexStringWithColons(byte[] bytes) {
        char[] hexArray =
                {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B',
                        'C', 'D', 'E', 'F'};
        char[] hexChars = new char[(bytes.length * 3) - 1];
        int v;

        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 3] = hexArray[v / 16];
            hexChars[j * 3 + 1] = hexArray[v % 16];

            if (j < bytes.length - 1) {
                hexChars[j * 3 + 2] = ':';
            }
        }

        return new String(hexChars);
    }

    /**
     * Confirms that the broadcast receiver for a given Intent
     * has the desired signature hash.
     * <p>
     * If you know the package name of the receiver, call
     * setPackage() on the Intent before passing into this method.
     * That will validate whether the package is installed and whether
     * it has the proper signature hash. You can distinguish between
     * these cases by passing true for the failIfHack parameter.
     * <p>
     * In general, there are three possible outcomes of calling
     * this method:
     * <p>
     * 1. You get a SecurityException, because failIfHack is true,
     * and we found some receiver whose app does not match the
     * desired hash. The user may have installed a repackaged
     * version of this app that is signed by the wrong key.
     * <p>
     * 2. You get null. If failIfHack is true, this means that no
     * receiver was found that matches the Intent. If failIfHack
     * is false, this means that no receiver was found that matches
     * the Intent and has a valid matching signature.
     * <p>
     * 3. You get an Intent. This means we found a matching receiver
     * that has a matching signature. The Intent will be a copy of
     * the passed-in Intent, with the component name set to the
     * matching receiver, so the "broadcast" will only go to this
     * one component.
     *
     * @param context    any Context will do; the value is not retained
     * @param toValidate the Intent that you intend to broadcast
     * @param sigHash    the signature hash of the app that you expect
     *                   to handle this broadcast
     * @param failIfHack true if you want a SecurityException if
     *                   a matching receiver is found but it has
     *                   the wrong signature hash, false otherwise
     * @return null if there is no matching receiver with the correct
     * hash, or a copy of the toValidate parameter with the full component
     * name of the target receiver added to the Intent
     */
    @Nullable
    public static Intent validateBroadcastIntent(Context context,
                                                 Intent toValidate,
                                                 String sigHash,
                                                 boolean failIfHack) {
        ArrayList<String> sigHashes = new ArrayList<String>();

        sigHashes.add(sigHash);

        return (validateBroadcastIntent(context, toValidate, sigHashes,
                failIfHack));
    }

    /**
     * Confirms that the broadcast receiver for a given Intent
     * has a desired signature hash.
     * <p>
     * If you know the package name of the receiver, call
     * setPackage() on the Intent before passing into this method.
     * That will validate whether the package is installed and whether
     * it has a proper signature hash. You can distinguish between
     * these cases by passing true for the failIfHack parameter.
     * <p>
     * In general, there are three possible outcomes of calling
     * this method:
     * <p>
     * 1. You get a SecurityException, because failIfHack is true,
     * and we found some receiver whose app does not match the
     * desired hash. The user may have installed a repackaged
     * version of this app that is signed by the wrong key.
     * <p>
     * 2. You get null. If failIfHack is true, this means that no
     * receiver was found that matches the Intent. If failIfHack
     * is false, this means that no receiver was found that matches
     * the Intent and has a valid matching signature.
     * <p>
     * 3. You get an Intent. This means we found a matching receiver
     * that has a matching signature. The Intent will be a copy of
     * the passed-in Intent, with the component name set to the
     * matching receiver, so the "broadcast" will only go to this
     * one component.
     *
     * @param context    any Context will do; the value is not retained
     * @param toValidate the Intent that you intend to broadcast
     * @param sigHashes  the possible signature hashes of the app
     *                   that you expect to handle this broadcast
     * @param failIfHack true if you want a SecurityException if
     *                   a matching receiver is found but it has
     *                   the wrong signature hash, false otherwise
     * @return null if there is no matching receiver with the correct
     * hash, or a copy of the toValidate parameter with the full component
     * name of the target receiver added to the Intent
     */
    @Nullable
    public static Intent validateBroadcastIntent(Context context,
                                                 Intent toValidate,
                                                 List<String> sigHashes,
                                                 boolean failIfHack) {
        PackageManager pm = context.getPackageManager();
        Intent result = null;
        List<ResolveInfo> receivers =
                pm.queryBroadcastReceivers(toValidate, 0);

        if (receivers != null) {
            for (ResolveInfo info : receivers) {
                try {
                    if (sigHashes.contains(getSignatureHash(context,
                            info.activityInfo.packageName))) {
                        ComponentName cn =
                                new ComponentName(info.activityInfo.packageName,
                                        info.activityInfo.name);

                        result = new Intent(toValidate).setComponent(cn);
                        break;
                    } else if (failIfHack) {
                        throw new SecurityException(
                                "Package has signature hash mismatch: " +
                                        info.activityInfo.packageName);
                    }
                } catch (NoSuchAlgorithmException e) {
                    Log.w("SignatureUtils",
                            "Exception when computing signature hash", e);
                } catch (NameNotFoundException e) {
                    Log.w("SignatureUtils",
                            "Exception when computing signature hash", e);
                }
            }
        }

        return (result);
    }

    /**
     * Confirms that the activity for a given Intent has the
     * desired signature hash.
     * <p>
     * If you know the package name of the activity, call
     * setPackage() on the Intent before passing into this method.
     * That will validate whether the package is installed and whether
     * it has the proper signature hash. You can distinguish between
     * these cases by passing true for the failIfHack parameter.
     * <p>
     * In general, there are three possible outcomes of calling
     * this method:
     * <p>
     * 1. You get a SecurityException, because failIfHack is true,
     * and we found some activity whose app does not match the
     * desired hash. The user may have installed a repackaged
     * version of this app that is signed by the wrong key.
     * <p>
     * 2. You get null. If failIfHack is true, this means that no
     * activity was found that matches the Intent. If failIfHack
     * is false, this means that no activity was found that matches
     * the Intent and has a valid matching signature.
     * <p>
     * 3. You get an Intent. This means we found a matching activity
     * that has a matching signature. The Intent will be a copy of
     * the passed-in Intent, with the component name set to the
     * matching activity, so a call to startActivity() for this
     * Intent is guaranteed to go to this specific activity.
     *
     * @param context    any Context will do; the value is not retained
     * @param toValidate the Intent that you intend to use with
     *                   startActivity()
     * @param sigHash    the signature hash of the app that you expect
     *                   to handle this activity
     * @param failIfHack true if you want a SecurityException if
     *                   a matching activity is found but it has
     *                   the wrong signature hash, false otherwise
     * @return null if there is no matching activity with the correct
     * hash, or a copy of the toValidate parameter with the full component
     * name of the target activity added to the Intent
     */
    @Nullable
    public static Intent validateActivityIntent(Context context,
                                                Intent toValidate,
                                                String sigHash,
                                                boolean failIfHack) {
        ArrayList<String> sigHashes = new ArrayList<String>();

        sigHashes.add(sigHash);

        return (validateActivityIntent(context, toValidate, sigHashes,
                failIfHack));
    }

    /**
     * Confirms that the activity for a given Intent has the
     * desired signature hash.
     * <p>
     * If you know the package name of the activity, call
     * setPackage() on the Intent before passing into this method.
     * That will validate whether the package is installed and whether
     * it has the proper signature hash. You can distinguish between
     * these cases by passing true for the failIfHack parameter.
     * <p>
     * In general, there are three possible outcomes of calling
     * this method:
     * <p>
     * 1. You get a SecurityException, because failIfHack is true,
     * and we found some activity whose app does not match the
     * desired hash. The user may have installed a repackaged
     * version of this app that is signed by the wrong key.
     * <p>
     * 2. You get null. If failIfHack is true, this means that no
     * activity was found that matches the Intent. If failIfHack
     * is false, this means that no activity was found that matches
     * the Intent and has a valid matching signature.
     * <p>
     * 3. You get an Intent. This means we found a matching activity
     * that has a matching signature. The Intent will be a copy of
     * the passed-in Intent, with the component name set to the
     * matching activity, so a call to startActivity() for this
     * Intent is guaranteed to go to this specific activity.
     *
     * @param context    any Context will do; the value is not retained
     * @param toValidate the Intent that you intend to use with
     *                   startActivity()
     * @param sigHashes  the signature hashes of the app that you expect
     *                   to handle this activity
     * @param failIfHack true if you want a SecurityException if
     *                   a matching activity is found but it has
     *                   the wrong signature hash, false otherwise
     * @return null if there is no matching activity with the correct
     * hash, or a copy of the toValidate parameter with the full component
     * name of the target activity added to the Intent
     */
    @Nullable
    public static Intent validateActivityIntent(Context context,
                                                Intent toValidate,
                                                List<String> sigHashes,
                                                boolean failIfHack) {
        PackageManager pm = context.getPackageManager();
        Intent result = null;
        List<ResolveInfo> activities =
                pm.queryIntentActivities(toValidate, 0);

        if (activities != null) {
            for (ResolveInfo info : activities) {
                try {
                    if (sigHashes.contains(getSignatureHash(context,
                            info.activityInfo.packageName))) {
                        ComponentName cn =
                                new ComponentName(info.activityInfo.packageName,
                                        info.activityInfo.name);

                        result = new Intent(toValidate).setComponent(cn);
                        break;
                    } else if (failIfHack) {
                        throw new SecurityException(
                                "Package has signature hash mismatch: " +
                                        info.activityInfo.packageName);
                    }
                } catch (NoSuchAlgorithmException e) {
                    Log.w("SignatureUtils",
                            "Exception when computing signature hash", e);
                } catch (NameNotFoundException e) {
                    Log.w("SignatureUtils",
                            "Exception when computing signature hash", e);
                }
            }
        }

        return (result);
    }

    /**
     * Confirms that the service for a given Intent has the
     * desired signature hash.
     * <p>
     * If you know the package name of the service, call
     * setPackage() on the Intent before passing into this method.
     * That will validate whether the package is installed and whether
     * it has the proper signature hash. You can distinguish between
     * these cases by passing true for the failIfHack parameter.
     * <p>
     * In general, there are three possible outcomes of calling
     * this method:
     * <p>
     * 1. You get a SecurityException, because failIfHack is true,
     * and we found some service whose app does not match the
     * desired hash. The user may have installed a repackaged
     * version of this app that is signed by the wrong key.
     * <p>
     * 2. You get null. If failIfHack is true, this means that no
     * service was found that matches the Intent. If failIfHack
     * is false, this means that no service was found that matches
     * the Intent and has a valid matching signature.
     * <p>
     * 3. You get an Intent. This means we found a matching service
     * that has a matching signature. The Intent will be a copy of
     * the passed-in Intent, with the component name set to the
     * matching service, so a call to startService() or
     * bindService() for this Intent is guaranteed to go to this
     * specific service.
     *
     * @param context    any Context will do; the value is not retained
     * @param toValidate the Intent that you intend to use with
     *                   startService() or bindService()
     * @param sigHash    the signature hash of the app that you expect
     *                   to handle this service
     * @param failIfHack true if you want a SecurityException if
     *                   a matching service is found but it has
     *                   the wrong signature hash, false otherwise
     * @return null if there is no matching service with the correct
     * hash, or a copy of the toValidate parameter with the full component
     * name of the target service added to the Intent
     */
    @Nullable
    public static Intent validateServiceIntent(Context context,
                                               Intent toValidate,
                                               String sigHash,
                                               boolean failIfHack) {
        ArrayList<String> sigHashes = new ArrayList<String>();

        sigHashes.add(sigHash);

        return (validateServiceIntent(context, toValidate, sigHashes,
                failIfHack));
    }

    /**
     * Confirms that the service for a given Intent has the
     * desired signature hash.
     * <p>
     * If you know the package name of the service, call
     * setPackage() on the Intent before passing into this method.
     * That will validate whether the package is installed and whether
     * it has the proper signature hash. You can distinguish between
     * these cases by passing true for the failIfHack parameter.
     * <p>
     * In general, there are three possible outcomes of calling
     * this method:
     * <p>
     * 1. You get a SecurityException, because failIfHack is true,
     * and we found some service whose app does not match the
     * desired hash. The user may have installed a repackaged
     * version of this app that is signed by the wrong key.
     * <p>
     * 2. You get null. If failIfHack is true, this means that no
     * service was found that matches the Intent. If failIfHack
     * is false, this means that no service was found that matches
     * the Intent and has a valid matching signature.
     * <p>
     * 3. You get an Intent. This means we found a matching service
     * that has a matching signature. The Intent will be a copy of
     * the passed-in Intent, with the component name set to the
     * matching service, so a call to startService() or
     * bindService() for this Intent is guaranteed to go to this
     * specific service.
     *
     * @param context    any Context will do; the value is not retained
     * @param toValidate the Intent that you intend to use with
     *                   startService() or bindService()
     * @param sigHashes  the signature hash of the app that you expect
     *                   to handle this service
     * @param failIfHack true if you want a SecurityException if
     *                   a matching service is found but it has
     *                   the wrong signature hash, false otherwise
     * @return null if there is no matching service with the correct
     * hash, or a copy of the toValidate parameter with the full component
     * name of the target service added to the Intent
     */
    @Nullable
    public static Intent validateServiceIntent(Context context,
                                               Intent toValidate,
                                               List<String> sigHashes,
                                               boolean failIfHack) {
        PackageManager pm = context.getPackageManager();
        Intent result = null;
        List<ResolveInfo> services =
                pm.queryIntentServices(toValidate, 0);

        if (services != null) {
            for (ResolveInfo info : services) {
                try {
                    if (sigHashes.contains(getSignatureHash(context,
                            info.serviceInfo.packageName))) {
                        ComponentName cn =
                                new ComponentName(info.serviceInfo.packageName,
                                        info.serviceInfo.name);

                        result = new Intent(toValidate).setComponent(cn);
                        break;
                    } else if (failIfHack) {
                        throw new SecurityException(
                                "Package has signature hash mismatch: " +
                                        info.activityInfo.packageName);
                    }
                } catch (NoSuchAlgorithmException e) {
                    Log.w("SignatureUtils",
                            "Exception when computing signature hash", e);
                } catch (NameNotFoundException e) {
                    Log.w("SignatureUtils",
                            "Exception when computing signature hash", e);
                }
            }
        }

        return (result);
    }
}
