
/*
 * ApkCloner - based on Android Manifest and resource.arsc package modification
 * Copyright 2025, developer-krushna
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of developer-krushna nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


 *     Please contact Krushna by email mt.modder.hub@gmail.com if you need
 *     additional information or have any questions
 */
 
package mt.modder.hub.apkCloner.util;

import android.content.*;
import android.text.TextUtils;

import java.io.*;
import java.util.*;

import bin.zip.ZipEntry;
import bin.zip.ZipFile;
import bin.zip.ZipOutputStream;

import com.apk.axml.aXMLDecoder;

import java.util.regex.*;
import org.xmlpull.v1.*;
import mt.modder.hub.arsc.*;

/* 
 Author @developer-krushna
 Comments by ChatGPT
 */
 
public class ApkCloner {
	// Paths for APK files and temporary directory
	private String outApk; // Path for the output APK
	private String srcApk; // Path for the source APK
	private String tempApk; // Path for the temporary directory
	private ApkClonerCallBack mCallBack; // Callback interface for progress and messages
	private String AndroidManifest = "AndroidManifest.xml"; // Name of the AndroidManifest file
	private String resourcesArsc = "resources.arsc";
	private String processing = "Processing ";
	private String OldPackageName;
	private String newPackageName;
	private Context mContext;

    public static String changeEndCharacter(final String text) {
        if (!(TextUtils.isEmpty(text))) {
            char lastChar = text.charAt(text.length() - 1);
            if (Character.isLowerCase(lastChar)) {
                String alphabet = "abcdefghijklmnopqrstuvwxyz";
                int replacementIndex = alphabet.indexOf(Character.toLowerCase(lastChar)) + 1;
                char replacementLetter = alphabet.charAt(replacementIndex % 26);
                return (text.substring(0, (text.length() - 1)) + replacementLetter);
            }
            if (Character.isDigit(lastChar)) {
                int lastDigit = Character.getNumericValue(lastChar);
                int nextDigit = (lastDigit + 1) % 10;
                return (text.substring((0), (text.length() - 1)) + nextDigit);
            }
            if (Character.isUpperCase(lastChar)) {
                String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                int replacementIndex = alphabet.indexOf(Character.toUpperCase(lastChar)) + 1;
                char replacementLetter = alphabet.charAt(replacementIndex % 26);
                return (text.substring((0), (text.length() - 1)) + replacementLetter);
            }
        }
        return (text);
    }
    public ApkCloner(Context context, ApkClonerCallBack callback) {
		mCallBack = callback; 
		mContext = context;
	}

	// Method to set paths for source APK, output APK, and temporary directory
	public void setPath(String input, String oldpkg, String newPkg) {
		srcApk = input;
		OldPackageName = oldpkg;
		newPackageName = newPkg;
		outApk = input.replace(".apk", "_clone.apk"); // Output APK with "_dp" suffix
		tempApk = new File(srcApk).getParentFile().toString() + "/.temp"; // Temporary directory path
	}

	// Main method to process the APK
	public void processApk() throws Exception {
		// Delete existing output APK file
		new File(outApk).delete();

		try (ZipFile zipFile = new ZipFile(srcApk)) { // Open source APK as a zip file
			
			// Prepare the temporary zip output stream
			ZipOutputStream zipOutputStream = new ZipOutputStream(new File(tempApk));
			zipOutputStream.setLevel(1); // Set compression level
			zipOutputStream.setMethod(ZipOutputStream.STORED); // Set storage method

			// Lists to track dex files and all files in the zip
			Enumeration<ZipEntry> entries = zipFile.getEntries();
			ArrayList<String> dexList = new ArrayList<>();
			ArrayList<String> totalFilesInZip = new ArrayList<>();

			// Copy specific entries to the temporary zip
			while (entries.hasMoreElements()) {
				ZipEntry nextElement = entries.nextElement();
				String name = nextElement.getName();
				totalFilesInZip.add(name);

				// Copy dex files and ignore other files
				if ((name.startsWith("classes") && name.endsWith("dex")) || name.startsWith("./")) {
					zipOutputStream.copyZipEntry(nextElement, zipFile);
					dexList.add(name);
				}
			}
			zipOutputStream.close();

			// Create the final output APK
			try (ZipOutputStream zos = new ZipOutputStream(new File(outApk))) {
				zos.setMethod(ZipOutputStream.DEFLATED);

				// Retrieve AndroidManifest entry from the APK
				ZipEntry manifestEntry = zipFile.getEntry(AndroidManifest);
				// Modify the AndroidManifest binary XML data
				mCallBack.onMessage(processing + AndroidManifest);
				byte[] manifestData = getModifiedBinXmlData(zipFile.getInputStream(manifestEntry), mContext, OldPackageName, newPackageName);
				
				// Add the modified AndroidManifest to the output APK
				zos.putNextEntry(AndroidManifest);
				zos.write(manifestData);
				zos.closeEntry();

				// Retrieve resources.arsc entry from the APK
				mCallBack.onMessage(processing + resourcesArsc);
				ZipEntry arscEntry = zipFile.getEntry(resourcesArsc);
                if(arscEntry != null && arscEntry.getSize() > 50) { // It's possible to have valid APK with no or empty ARSC!
                    byte[] arscData = processArsc(zipFile.getInputStream(arscEntry));
                    zos.putNextEntry(resourcesArsc);
                    // Write arsc data with progress tracking
                    ByteArrayInputStream bis = new ByteArrayInputStream(arscData);
                    byte[] buffer = new byte[2048];
                    int length;
                    int totalBytes = arscData.length;
                    int copiedBytes = 0;
                    while ((length = bis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                        copiedBytes += length;
                        mCallBack.onProgress(copiedBytes, totalBytes); // Update progress
                    }
                }
				zos.closeEntry();

				// Copy other files from the temporary zip to the final output APK
				mCallBack.onMessage(processing.replace(" ","") + "...");
				Enumeration<ZipEntry> entry = zipFile.getEntries();
				int copiedFiles = 0;
				while (entry.hasMoreElements()) {
					ZipEntry newEntry = entry.nextElement();
					// Avoid duplicating the AndroidManifest and resources.arsc
					if (!newEntry.getName().equals(AndroidManifest) && !newEntry.getName().equals(resourcesArsc)) {
						zos.copyZipEntry(newEntry, zipFile);
						copiedFiles++;
						mCallBack.onProgress(copiedFiles, totalFilesInZip.size()); // Update progress
					}
				}

				// Clean up the temporary directory
				new File(tempApk).delete();
				zipFile.close();
			} 
		}
	}

	// Method to process and return new arsc file data
	private byte [] processArsc(InputStream arsc) throws Exception {
		BinaryResourceFile resourceFile = BinaryResourceFile.fromInputStream(arsc);
		for (Chunk chunk : resourceFile.getChunks()) {
			ResourceTableChunk tableChunk = (ResourceTableChunk) chunk;
			for (PackageChunk packageChunk : tableChunk.getPackages()) {
				packageChunk.setPackageName(newPackageName.trim());		
			}
		}
		return resourceFile.toByteArray();

	}
	
	// Utility method to read all bytes from an InputStream
	public static byte[] readAllBytes(InputStream is) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buffer = new byte[2048];
		int len;
		while ((len = is.read(buffer)) > 0)
			bos.write(buffer, 0, len);
		is.close();
		return bos.toByteArray();
	}

	// Method to modify AndroidManifest binary XML data
	public static byte[] getModifiedBinXmlData(InputStream inputStream, Context context, String oldPackageName, String newPackageName)
	throws IOException, XmlPullParserException {
        /*AXMLPrinter axmlPrinter = new AXMLPrinter();
        axmlPrinter.setEnableID2Name(false);
        axmlPrinter.setAttrValueTranslation(false);
        axmlPrinter.setExtractPermissionDescription(false);*/
        String axmlString = new aXMLDecoder(inputStream).decodeAsString(); //axmlPrinter.convertXml(readAllBytes(inputStream));

        // Replace package name
        String replacedPackageString = axmlString.replaceFirst("package=\"" + oldPackageName + "\"", "package=\"" + newPackageName + "\"");

        HashMap<String, String> permissionMap = new HashMap<>();

        // --- SECTION 1: Modify <permission> tags ---
        // This regex finds <permission> tags and extracts the value of the android:name attribute.
        // It then modifies these permission names, prepending the new package name if they start with the old one,
        // or adding it with an underscore otherwise. This also maps the old to new name.
		Matcher permissionMatcher = Pattern.compile("<permission[^>]*?android:name=\"(.*?)\"").matcher(replacedPackageString);
		StringBuilder permissionSB = new StringBuilder(replacedPackageString.length());
		int lastPermissionMatchEnd = 0;

        // Loop through all the <permission> tags
		while (permissionMatcher.find()) {
			// Get the permission name
			String permissionName = permissionMatcher.group(1);
			// Get the start index of the permission name string
			int permissionStart = permissionMatcher.start(1);
			// Get the end index of the permission name string
			int permissionEnd = permissionMatcher.end(1);

			// Append the part of the string before the current <permission> tag
			permissionSB.append(replacedPackageString.substring(lastPermissionMatchEnd, permissionStart));

			// Generate a modified permission name
			String modifiedPermissionName = permissionName.startsWith(oldPackageName) ?
				newPackageName + permissionName.substring(oldPackageName.length()) :
				newPackageName + "_" + permissionName;

			// Append the modified permission name
			permissionSB.append(modifiedPermissionName);
			// Store mapping from old permission to modified permission name for later use
			permissionMap.put(permissionName, modifiedPermissionName);
			// Update last processed index
			lastPermissionMatchEnd = permissionEnd;
		}

        // Append any remaining part of the input string after the last <permission> tag to StringBuilder
		permissionSB.append(replacedPackageString.substring(lastPermissionMatchEnd));
		String modifiedPermissionsString = permissionSB.toString();


         // --- SECTION 2: Modify <uses-permission> tags ---
        // This regex finds <uses-permission> tags and extracts the value of the android:name attribute.
        // It uses the mapping of old to new name generated in previous step to modify permission names
        // It also modifies those that start with old packagename.
		Matcher usesPermissionMatcher = Pattern.compile("<uses-permission[^>]*?android:name=\"(.*?)\"").matcher(modifiedPermissionsString);
		StringBuilder usesPermissionSB = new StringBuilder(modifiedPermissionsString.length());
		int lastUsesPermissionMatchEnd = 0;
		
		// Loop through all <uses-permission> tags
		while (usesPermissionMatcher.find()) {
			String usesPermissionName = usesPermissionMatcher.group(1);
			// Get start index of the uses-permission name
			int usesPermissionStart = usesPermissionMatcher.start(1);
			// Get end index of the uses-permission name
			int usesPermissionEnd = usesPermissionMatcher.end(1);

			// Append the part of the string before the current <uses-permission> tag
			usesPermissionSB.append(modifiedPermissionsString.substring(lastUsesPermissionMatchEnd, usesPermissionStart));

			// Check if it is not an android standard permission
			if (!usesPermissionName.startsWith("android.") && !usesPermissionName.startsWith("com.android.")) {
				// if current permission name exists in map from the <permission> modifications above
				// then apply same mapping.
				if (permissionMap.containsKey(usesPermissionName)) {
					usesPermissionSB.append(permissionMap.get(usesPermissionName));
					// If uses permission starts with old package name, apply new package name with the existing remaining text
				} else if (usesPermissionName.startsWith(oldPackageName)) {
					usesPermissionSB.append(newPackageName);
					usesPermissionSB.append(usesPermissionName.substring(oldPackageName.length()));
					// Otherwise, prepend the new package name with an underscore.
				} else {
					usesPermissionSB.append(newPackageName);
					usesPermissionSB.append("_");
					usesPermissionSB.append(usesPermissionName);
				}
				// If it's an android default permission, preserve the permission name.
			}else{
				usesPermissionSB.append(usesPermissionName);
			}
			// update last index
			lastUsesPermissionMatchEnd = usesPermissionEnd;
		}
        // Append any remaining part of the input string after the last <uses-permission> tag
		usesPermissionSB.append(modifiedPermissionsString.substring(lastUsesPermissionMatchEnd));
		String modifiedUsesPermissionsString = usesPermissionSB.toString();
        
        // Find and modify provider authorities
        // This regex finds <provider> tags and extracts the value of the android:authorities attribute.
        // It then modifies these authorities, prepending the new package name if they start with the old one,
        // or adding it with an underscore otherwise. This is useful for updating authorities during package renaming.
		Matcher providerMatcher = Pattern.compile("<provider[^>]*?android:authorities=\"(.*?)\"").matcher(modifiedUsesPermissionsString);
		StringBuilder providerSB = new StringBuilder();
		int lastProviderMatchEnd = 0;

        // Loop through all the matching provider tags
		while (providerMatcher.find()) {
			// Extract the authorities string. Multiple authorities can be separated by semicolons
			String[] authorities = providerMatcher.group(1).split(";");
			// Get the starting index of the authorities string within the full input string
			int providerStart = providerMatcher.start(1);
			// Get the end index of the authorities string within the full input string
			int providerEnd = providerMatcher.end(1);

			// Append the part of the input string before the current match to the StringBuilder
			providerSB.append(modifiedUsesPermissionsString.substring(lastProviderMatchEnd, providerStart));

			// Iterate over individual authorities
			for (int i = 0; i < authorities.length; i++) {
				String authority = authorities[i];
				// Add semicolon separation for each authorities
				if (i > 0) {
					providerSB.append(";");
				}
				// If authority starts with the old package name, prepend new package name
				// and preserve the remainder of the authority string.
				if (authority.startsWith(oldPackageName)) {
					providerSB.append(newPackageName);
					providerSB.append(authority.substring(oldPackageName.length()));
					// If authority doesn't start with the old package name, add new package name with underscore
				} else {
					providerSB.append(newPackageName);
					providerSB.append("_");
					providerSB.append(authority);
				}
			}
			// Update the last processed index with the end of current authorities.
			lastProviderMatchEnd = providerEnd;
		}
        // Append any remaining part of the input string after the last provider tag to StringBuilder
		providerSB.append(modifiedUsesPermissionsString.substring(lastProviderMatchEnd));
		String modifiedProvidersString =  providerSB.toString();
		

        // Find and modify relative class names
		// This regex finds Android component tags (application, activity, service, receiver, activity-alias)
        // and extracts the relative class name (starting with a dot) specified in the android:name attribute.
        Matcher relativeClassNameMatcher = Pattern.compile("<(?:application|activity|service|receiver|activity-alias)[^>]*?android:name=\"(\\..*?)\"").matcher(modifiedProvidersString);
        StringBuilder relativeClassNameSB = new StringBuilder(modifiedProvidersString.length());
        int lastRelativeClassNameMatchEnd = 0;
		while (relativeClassNameMatcher.find()) {
			int relativeClassNameStart = relativeClassNameMatcher.start(1);

			relativeClassNameSB.append(modifiedProvidersString.substring(lastRelativeClassNameMatchEnd, relativeClassNameStart));
			relativeClassNameSB.append(oldPackageName);
            lastRelativeClassNameMatchEnd = relativeClassNameStart;
        }
        relativeClassNameSB.append(modifiedProvidersString.substring(lastRelativeClassNameMatchEnd));
		String modifiedRelativeClassNameString = relativeClassNameSB.toString();


        // Find and modify activity alias target activity
		// This regex finds <activity-alias> tags and extracts the relative class name (starting with a dot)
        // specified in the android:targetActivity attribute. This is useful for finding aliased activities in Android manifests.
        Matcher activityAliasMatcher = Pattern.compile("<activity-alias[^>]*?android:targetActivity=\"(\\..*?)\"").matcher(modifiedRelativeClassNameString);
        StringBuilder activityAliasSB = new StringBuilder(modifiedRelativeClassNameString.length());
        int lastActivityAliasMatchEnd = 0;
        while (activityAliasMatcher.find()) {
            int activityAliasStart = activityAliasMatcher.start(1);
            activityAliasSB.append(modifiedRelativeClassNameString.substring(lastActivityAliasMatchEnd, activityAliasStart));
			activityAliasSB.append(oldPackageName);
			lastActivityAliasMatchEnd = activityAliasStart;
        }
        activityAliasSB.append(modifiedRelativeClassNameString.substring(lastActivityAliasMatchEnd));

        return new com.apk.axml.aXMLEncoder().encodeString(activityAliasSB.toString(), context);
        //return new AXMLCompiler().axml2Xml(context, activityAliasSB.toString());
    }
	

	// Callback interface for progress and messages
	public interface ApkClonerCallBack {
		void onProgress(int progress, int total); // Progress update method
		void onMessage(String name); // Message notification method
	}
}

