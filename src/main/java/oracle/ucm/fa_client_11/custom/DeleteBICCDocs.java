/*  Java Code Snippet to delete BICC Data Extract Files in UCM
    Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved
    Snippet created by Ulrich Janke, Oracle A-Team on 28-Mar-2018
    Important: you have to modify the code before you can compile it! Its just a sample we're providing here!
    Description :
      - program takes three arguments
          1) <UCM connection property file> - connection.properties from WebCenter Content Document Transfer Utility for RIDC library
                containing URL, user name, password and policy to access Fusion SaaS UCM
          2) <location for MANIFEST.MF> - Manifest File with the UCM Doc ID's from a BICC data extraction run (absolute path to the file)
          3) <DocID for MANIFEST.MF> - UCM Doc ID for the MANIFEST.MF as this information is not available in the Manifest file itself
      - after validation of readability of input files and checking UCM connection the program goes through all the entries in MANIFEST.MF
        file and calls the UCM service to delete the data extraction file via its UCM Document ID
      - MANIFEST.MF file will be deleted as the last one
    Dependencies:
      - UCM RIDC Java Library from WebCenter Content Document Transfer Utility oracle.ucm.fa_client_11.1.1.jar
      - Apache commons-codec-1.11 Java Library http://www-us.apache.org/dist/commons/codec/binaries/commons-codec-1.11-bin.zip
      - Apache commons-httpclient-3.1 Java Library http://www.apache.org/dist/httpcomponents/commons-httpclient/binary/commons-httpclient-3.1.zip
      - Apache commons-logging-1.2 Java Library http://www-us.apache.org/dist/commons/logging/binaries/commons-logging-1.2-bin.zip
    Known limitations:
      - the RIDC service API doesn't return a statement whether the file has been deleted successfully or not
      - the project code checks for the HTTP response code and will break the further execution if its not status 200
      - HTTP-200 will be returned independently of the result that a file in UCM has been deleted or not
      - means the program can run as often as possible and it will evert return HTTP-200 even if the file doesn't exist anymore in UCM
      - it is recommended to check the status of a data extraction file's existence from outside this program vis the SearchTool included in
        the WebCenter Content Document Transfer Utility
*/
package oracle.ucm.fa_client_11.custom;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collection;
import java.util.Iterator;

import oracle.stellent.ridc.IdcClient;
import oracle.stellent.ridc.IdcClientException;
import oracle.stellent.ridc.IdcClientManager;
import oracle.stellent.ridc.IdcContext;
import oracle.stellent.ridc.model.DataBinder;
import oracle.stellent.ridc.protocol.ServiceResponse;


/* Organize your imports! You need to import:
 - oracle.stellent.ridc.IdcClient
 - oracle.stellent.ridc.IdcClientException
 - oracle.stellent.ridc.IdcClientManager
 - oracle.stellent.ridc.IdcContext
 - oracle.stellent.ridc.model.DataBinder
 - oracle.stellent.ridc.protocol.ServiceResponse
Don't forget to add the Apache libraries and RIDC library from Webcenter Document Transfer Utility to you CLASSPATH */
class DeleteBICCDocs {
    public DeleteBICCDocs() {
        super();
    }

    public static void main(String[] argv) throws IOException {
        String[] errMsg;
        if (argv.length != 3) {
            errMsg = new String[]{"Error: Incorrect number of parameters!"};
            printErrorUsage(errMsg);
            return;
        }
        char[] connInfo, manifestInfo;
        IdcContext idcContext;
        FileReader connFileReader, manifestFileReader;
        File connFile, manifestFile;
        String manifestDocID, connFileName, manifestFileName;
        connFileName = argv[0];
        manifestFileName = argv[1];
        connFile = new File(argv[0]);
        manifestFile = new File(argv[1]);
        manifestDocID = new String(argv[2]);
        for (char c : manifestDocID.toCharArray()) {
            if (!Character.isDigit(c)) {
                errMsg = new String[]{"Error: Argument >" + manifestDocID + "< must contain numbers only!"};
                printErrorUsage(errMsg);
                return;
            }
        }
        if (!checkFile(connFile, connFileName)) return;
        if (!checkFile(manifestFile, manifestFileName)) return;
        try {
            connFileReader = new FileReader(connFile);
            manifestFileReader = new FileReader(manifestFile);
            connInfo = new char[(int) connFile.length()];
            manifestInfo = new char[(int) manifestFile.length()];
            connFileReader.read(connInfo);
            manifestFileReader.read(manifestInfo);
        } catch (FileNotFoundException fnfe) {
            System.err.print("Exception: FileNotFound");
            System.err.print(fnfe.getMessage());
            return;
        } catch (IOException ioe) {
            System.err.print("Exception: IOException");
            System.err.print(ioe.getMessage());
            return;
        }
        System.out.print("Running DeleteBICCDocs ...");
        System.out.print("  Deleting files as contained in file >" + argv[1] + "< ...");
        String[] connDetails = getConnDetails(connInfo);
        System.out.print("  UCM URL is >" + connDetails[0] + "< and user name is >" + connDetails[1] + "< ...");
        String[] docInfo = getDeletionInfo(manifestInfo);
        System.out.print("  File >" + manifestFileName + "< contains >" + (docInfo.length) + "< files to delete in UCM ...");
        try {
            System.out.print("    Connect to " + connDetails[0] + " ...");
            idcContext = doConnect(connDetails);
        } catch (IdcClientException ice) {
            System.out.print("Exception: IdcClientException when creating an UCM connection!");
            System.err.print(ice.getMessage());
            return;
        }
        if (!doDeleteDocs(idcContext, docInfo, connDetails[0], manifestDocID)) {
            System.err.print("  <<< Deletion of docs in UCM failed!!! >>>");
            System.out.print("UCM file deletion aborted");
        } else {
            System.out.print("  Docs deletion in UCM finished!");
            System.out.print("UCM file deletion successfully finished!");
        }
        // ... add any other Exception handlers as necessary
        return;
    }

    // parse the connection.properties file (values passed as a char[]) for the connection information
    // this handling of the connection details is a standard feature of the Webcenter Document Transfer Utility
    private static String[] getConnDetails(char[] connInfo) {
        String fullInfo, userName, passWord, ucmURL, policy;
        String[] infoArr;
        String[] retArr = new String[4];
        ucmURL = "url";
        userName = "username";
        passWord = "password";
        policy = "policy";
        fullInfo = new String(connInfo);
        infoArr = fullInfo.split("\n");
        for (String connLine : infoArr) {
            String[] matchLine = connLine.split("=");
            // zero element is the URL
            if (matchLine[0].equalsIgnoreCase(ucmURL)) retArr[0] = matchLine[1];
            // first element is the user name
            if (matchLine[0].equalsIgnoreCase(userName)) retArr[1] = matchLine[1];
            // second element is the password
            if (matchLine[0].equalsIgnoreCase(passWord)) retArr[2] = matchLine[1];
            // third element is the policy
            if (matchLine[0].equalsIgnoreCase(policy)) retArr[3] = matchLine[1];
        }
        return retArr;
    }

    protected static void printErrorUsage(String[] msgArr) {
        for (String msgArr1 : msgArr) {
            System.out.print(msgArr1);
        }
        System.out.print("Usage: DeleteBICCDocs <UCM connection property file> <location for MANIFEST.MF> <DocID for MANIFEST.MF>");
    }

    protected static IdcContext doConnect(String[] connDetails) throws IdcClientException {
        IdcContext idcContext;
        idcContext = new IdcContext(connDetails[1], connDetails[2]);
        return idcContext;
    }

    protected static Boolean doDeleteDocs(IdcContext idcContext, String[] docInfoArr, String url, String manifestDocID) {
        IdcClient idcClient;
        IdcClientManager clientManager;
        DataBinder binder;
        int numUCMFiles = docInfoArr.length;
        String[] docsToDelete = new String[numUCMFiles + 1];
        int i, n = 0;
        for (i = 0; i < numUCMFiles; i++)
            docsToDelete[i] = docInfoArr[i];
        docsToDelete[numUCMFiles] = "MANIFEST.MF;" + manifestDocID + ";";
        try {
            clientManager = new IdcClientManager();
            idcClient = clientManager.createClient(url);
            idcClient.initialize();
            binder = idcClient.createBinder();
            String ucmFileName;
            String ucmDocID;
            ServiceResponse ucmResponse;
            for (String docLine : docsToDelete) {
                if (n >= 1) {
                    String[] matchLine = docLine.split(";");
                    // zero element is the file name
                    ucmFileName = matchLine[0];
                    // first element is the UCM Doc ID
                    ucmDocID = matchLine[1];
                    System.out.print("    ... deleting file >" + ucmFileName + "< with UCM DocID >" + ucmDocID + "< from UCM");
                    binder.putLocal("IdcService", "DELETE_DOC");
                    binder.putLocal("dID", ucmDocID);
                    ucmResponse = idcClient.sendRequest(idcContext, binder);
                    Collection<String> headerNames = ucmResponse.getHeaderNames();
                    Iterator<String> headNameIt = headerNames.iterator();
                    String headValue;
                    String[] valueDetails;
                    // check the HTTP Header of response
                    while (headNameIt.hasNext()) {
                        headValue = ucmResponse.getHeader(headNameIt.next());
                        valueDetails = headValue.split(" ");
                        // if the header name contains HTTP/1.1 we look for the status
                        if (valueDetails[0].equalsIgnoreCase("HTTP/1.1")) {
                            // if status is not 200 we break the program here
                            if (!valueDetails[1].equalsIgnoreCase("200")) {
                                System.out.print("     ... ----> Issue with HTTP request status, return value expected = 200, status returned = " + valueDetails[1]);
                                System.out.print("Aborting!!!");
                                return false;
                            }
                        }
                    }
                }
                n++;
            }
        }
        // add all appropriate exception handlers as necessary - one sample is posted below
        catch (IdcClientException ice) {
            System.out.print("Error: IdcClientException in doDeleteDoc");
            System.out.print("   " + (n - 2) + " of " + numUCMFiles + " files deleted in UCM!");
            System.err.print(ice.getMessage());
            return false;
        }
        System.out.print("   " + (n - 1) + " deletion requests sent to UCM!");
        return true;
    }

    private static String[] getDeletionInfo(char[] manifestInfo) {
        String fileInfoArr;
        String[] docArr;
        fileInfoArr = new String(manifestInfo);
        docArr = fileInfoArr.split("\n");
        return docArr;
    }

    private static boolean checkFile(File fileObj, String fileName) {
        String[] errMsg;
        if (!fileObj.isFile()) {
            errMsg = new String[]{"Error: File >" + fileName + "< is not a file!"};
            printErrorUsage(errMsg);
            return false;
        }
        if (!fileObj.canRead()) {
            errMsg = new String[]{"Error: File >" + fileName + "< is not readable!"};
            printErrorUsage(errMsg);
            return false;
        }
        if (fileObj.length() == 0) {
            errMsg = new String[]{"Error: File >" + fileName + "< is empty!"};
            printErrorUsage(errMsg);
            return false;
        }
        return true;
    }
}