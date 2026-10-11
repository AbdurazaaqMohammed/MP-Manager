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

import com.apk.axml.APKParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.ResEntry;

import java.util.regex.*;
import org.xmlpull.v1.*;
import mt.modder.hub.arsc.*;

/* 
 Author @developer-krushna
 Comments by ChatGPT
 Fixed by Kentang999 - 4 critical bugs fixed
 */
 
public class ApkCloner {
	// Paths for APK files and temporary directory
	private String outApk; // Path for the output APK
	private String srcApk; // Path for the source APK
	private String tempApk; // Path for the temporary directory
	private final ApkClonerCallBack mCallBack; // Callback interface for progress and messages
	private final String AndroidManifest = "AndroidManifest.xml"; // Name of the AndroidManifest file
	private final String resourcesArsc = "resources.arsc";
	private final String processing = "Processing ";
	private String OldPackageName;
	private String newPackageName;
	private final Context mContext;

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
		outApk = input.replace(".apk", "_clone.apk"); // Output APK with "_clone" suffix
		tempApk = new File(srcApk).getParentFile().toString() + "/.temp"; // Temporary directory path
	}

	// Main method to process the APK
	public void processApk() throws Exception {
		// === VALIDASI INPUT ===
		if (TextUtils.isEmpty(srcApk) || TextUtils.isEmpty(OldPackageName) 
				|| TextUtils.isEmpty(newPackageName)) {
			throw new IOException("Input tidak valid: srcApk/OldPackageName/newPackageName kosong");
		}
		if (OldPackageName.equals(newPackageName)) {
			throw new IOException("Package name baru sama dengan yang lama. Ubah dulu!");
		}
		File srcFile = new File(srcApk);
		if (!srcFile.exists() || srcFile.length() == 0) {
			throw new IOException("APK sumber tidak ditemukan atau kosong");
		}

		// Delete existing output APK file
		new File(outApk).delete();
		// Clean temp file if exists
		File tempFile = new File(tempApk);
		if (tempFile.exists()) tempFile.delete();

		try (ZipFile zipFile = new ZipFile(srcApk)) {
			
			// Prepare the temporary zip output stream
			ZipOutputStream zipOutputStream = new ZipOutputStream(new File(tempApk));
			zipOutputStream.setLevel(1);
			zipOutputStream.setMethod(ZipOutputStream.STORED);

			Enumeration<ZipEntry> entries = zipFile.getEntries();
			ArrayList<String> dexList = new ArrayList<>();
			ArrayList<String> totalFilesInZip = new ArrayList<>();

			// Copy dex files and other files to temp
			while (entries.hasMoreElements()) {
				ZipEntry nextElement = entries.nextElement();
				String name = nextElement.getName();
				totalFilesInZip.add(name);
				if ((name.startsWith("classes") && name.endsWith("dex")) || name.startsWith("./")) {
					zipOutputStream.copyZipEntry(nextElement, zipFile);
					dexList.add(name);
				}
			}
			zipOutputStream.close();

			// Create the final output APK
			try (ZipOutputStream zos = new ZipOutputStream(new File(outApk))) {
				zos.setMethod(ZipOutputStream.DEFLATED);

				// === 1. Process AndroidManifest.xml ===
				ZipEntry manifestEntry = zipFile.getEntry(AndroidManifest);
				if (manifestEntry == null) {
					throw new IOException("AndroidManifest.xml tidak ditemukan di APK");
				}
				mCallBack.onMessage(processing + AndroidManifest);

				// Ambil resource table buat encode
				List<ResEntry> resEntries = null;
				try {
					APKParser apkParser = new APKParser();
					apkParser.parse(srcApk, mContext);
					resEntries = apkParser.getDecodedResources();
				} catch (Exception ignored) { }

				byte[] manifestData = getModifiedBinXmlData(
						zipFile.getInputStream(manifestEntry), mContext, 
						OldPackageName, newPackageName, resEntries);

				// === 2. Process resources.arsc ===
				mCallBack.onMessage(processing + resourcesArsc);
				ZipEntry arscEntry = zipFile.getEntry(resourcesArsc);
				byte[] arscData = null;
				if (arscEntry != null && arscEntry.getSize() > 50) {
					arscData = processArsc(zipFile.getInputStream(arscEntry));
				}

				// === 3. Tulis ke output: manifest DULU ===
				zos.putNextEntry(AndroidManifest);
				zos.write(manifestData);
				zos.closeEntry();

				// === BUG FIX 1: Hanya tulis arsc kalau ada ===
				if (arscData != null) {
					zos.putNextEntry(resourcesArsc);
					ByteArrayInputStream bis = new ByteArrayInputStream(arscData);
					byte[] buffer = new byte[2048];
					int length;
					int totalBytes = arscData.length;
					int copiedBytes = 0;
					while ((length = bis.read(buffer)) > 0) {
						zos.write(buffer, 0, length);
						copiedBytes += length;
						mCallBack.onProgress(copiedBytes, totalBytes);
					}
					zos.closeEntry();
				}

				// === 4. Copy file lain (skip manifest & arsc) ===
				mCallBack.onMessage(processing.replace(" ","") + "...");
				Enumeration<ZipEntry> entry = zipFile.getEntries();
				int copiedFiles = 0;
				while (entry.hasMoreElements()) {
					ZipEntry newEntry = entry.nextElement();
					if (!newEntry.getName().equals(AndroidManifest) 
							&& !newEntry.getName().equals(resourcesArsc)) {
						zos.copyZipEntry(newEntry, zipFile);
						copiedFiles++;
						mCallBack.onProgress(copiedFiles, totalFilesInZip.size());
					}
				}
			}

			// Clean up
			if (tempFile.exists()) tempFile.delete();

			// === BUG FIX 2: Verifikasi output ===
			File outFile = new File(outApk);
			if (!outFile.exists() || outFile.length() == 0) {
				throw new IOException("Clone gagal: output APK kosong");
			}
			try (ZipFile checkZip = new ZipFile(outApk)) {
				if (checkZip.getEntry(AndroidManifest) == null) {
					throw new IOException("Clone gagal: AndroidManifest.xml tidak ada di output");
				}
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new IOException("Clone gagal: output bukan zip valid — " + e.getMessage());
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

	// Escape regex special chars (BUG FIX 4)
	private static String escapeRegex(String input) {
		if (input == null) return "";
		return input.replaceAll("([\\\\.\\[\\]{}()*+?^$|])", "\\\\$1");
	}

	// Method to modify AndroidManifest binary XML data
	public static byte[] getModifiedBinXmlData(InputStream inputStream, Context context, 
			String oldPackageName, String newPackageName, List<ResEntry> resEntries)
	throws IOException, XmlPullParserException {
        String axmlString = new aXMLDecoder(inputStream).decodeAsString();

        // BUG FIX 4: Escape regex special chars
        String escapedOldPkg = escapeRegex(oldPackageName);
        String replacedPackageString = axmlString.replaceFirst(
				"package=\"" + escapedOldPkg + "\"", 
				"package=\"" + newPackageName + "\"");

        HashMap<String, String> permissionMap = new HashMap<>();

        // --- SECTION 1: Modify <permission> tags ---
		Matcher permissionMatcher = Pattern.compile("<permission[^>]*?android:name=\"(.*?)\"").matcher(replacedPackageString);
		StringBuilder permissionSB = new StringBuilder(replacedPackageString.length());
		int lastPermissionMatchEnd = 0;

		while (permissionMatcher.find()) {
			String permissionName = permissionMatcher.group(1);
			int permissionStart = permissionMatcher.start(1);
			int permissionEnd = permissionMatcher.end(1);

			permissionSB.append(replacedPackageString.substring(lastPermissionMatchEnd, permissionStart));

			String modifiedPermissionName = permissionName.startsWith(oldPackageName) ?
				newPackageName + permissionName.substring(oldPackageName.length()) :
				newPackageName + "_" + permissionName;

			permissionSB.append(modifiedPermissionName);
			permissionMap.put(permissionName, modifiedPermissionName);
			lastPermissionMatchEnd = permissionEnd;
		}

		permissionSB.append(replacedPackageString.substring(lastPermissionMatchEnd));
		String modifiedPermissionsString = permissionSB.toString();

         // --- SECTION 2: Modify <uses-permission> tags ---
		Matcher usesPermissionMatcher = Pattern.compile("<uses-permission[^>]*?android:name=\"(.*?)\"").matcher(modifiedPermissionsString);
		StringBuilder usesPermissionSB = new StringBuilder(modifiedPermissionsString.length());
		int lastUsesPermissionMatchEnd = 0;
		
		while (usesPermissionMatcher.find()) {
			String usesPermissionName = usesPermissionMatcher.group(1);
			int usesPermissionStart = usesPermissionMatcher.start(1);
			int usesPermissionEnd = usesPermissionMatcher.end(1);

			usesPermissionSB.append(modifiedPermissionsString.substring(lastUsesPermissionMatchEnd, usesPermissionStart));

			if (!usesPermissionName.startsWith("android.") && !usesPermissionName.startsWith("com.android.")) {
				if (permissionMap.containsKey(usesPermissionName)) {
					usesPermissionSB.append(permissionMap.get(usesPermissionName));
				} else if (usesPermissionName.startsWith(oldPackageName)) {
					usesPermissionSB.append(newPackageName);
					usesPermissionSB.append(usesPermissionName.substring(oldPackageName.length()));
				} else {
					usesPermissionSB.append(newPackageName);
					usesPermissionSB.append("_");
					usesPermissionSB.append(usesPermissionName);
				}
			} else {
				usesPermissionSB.append(usesPermissionName);
			}
			lastUsesPermissionMatchEnd = usesPermissionEnd;
		}
		usesPermissionSB.append(modifiedPermissionsString.substring(lastUsesPermissionMatchEnd));
		String modifiedUsesPermissionsString = usesPermissionSB.toString();
        
        // --- SECTION 3: Modify provider authorities ---
		Matcher providerMatcher = Pattern.compile("<provider[^>]*?android:authorities=\"(.*?)\"").matcher(modifiedUsesPermissionsString);
		StringBuilder providerSB = new StringBuilder();
		int lastProviderMatchEnd = 0;

		while (providerMatcher.find()) {
			String[] authorities = providerMatcher.group(1).split(";");
			int providerStart = providerMatcher.start(1);
			int providerEnd = providerMatcher.end(1);

			providerSB.append(modifiedUsesPermissionsString.substring(lastProviderMatchEnd, providerStart));

			for (int i = 0; i < authorities.length; i++) {
				String authority = authorities[i];
				if (i > 0) providerSB.append(";");
				if (authority.startsWith(oldPackageName)) {
					providerSB.append(newPackageName);
					providerSB.append(authority.substring(oldPackageName.length()));
				} else {
					providerSB.append(newPackageName);
					providerSB.append("_");
					providerSB.append(authority);
				}
			}
			lastProviderMatchEnd = providerEnd;
		}
		providerSB.append(modifiedUsesPermissionsString.substring(lastProviderMatchEnd));
		String modifiedProvidersString = providerSB.toString();

        // --- SECTION 4: Modify relative class names ---
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

        // --- SECTION 5: Modify activity-alias target ---
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

		// BUG FIX 5: Encode pakai resource table supaya @string/@drawable tidak jadi 0
		if (resEntries != null && !resEntries.isEmpty()) {
			return new aXMLEncoder().encodeString(activityAliasSB.toString(), context, resEntries);
		} else {
			return new aXMLEncoder().encodeString(activityAliasSB.toString(), context);
		}
    }

	// Callback interface for progress and messages
	public interface ApkClonerCallBack {
		void onProgress(int progress, int total);
		void onMessage(String name);
	}
}
