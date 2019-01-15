package com.groupdocs.ui.comparison.resources;

import com.groupdocs.comparison.common.license.License;
import com.groupdocs.ui.common.config.GlobalConfiguration;
import com.groupdocs.ui.common.entity.web.FileDescriptionEntity;
import com.groupdocs.ui.common.entity.web.PageDescriptionEntity;
import com.groupdocs.ui.common.entity.web.UploadedDocumentEntity;
import com.groupdocs.ui.common.entity.web.request.FileTreeRequest;
import com.groupdocs.ui.common.exception.TotalGroupDocsException;
import com.groupdocs.ui.common.resources.Resources;
import com.groupdocs.ui.comparison.model.request.CompareFileDataRequest;
import com.groupdocs.ui.comparison.model.request.CompareRequest;
import com.groupdocs.ui.comparison.model.request.LoadResultPageRequest;
import com.groupdocs.ui.comparison.model.response.CompareResultResponse;
import com.groupdocs.ui.comparison.service.ComparisonService;
import com.groupdocs.ui.comparison.service.ComparisonServiceImpl;
import com.groupdocs.ui.comparison.views.Comparison;
import org.apache.commons.io.FilenameUtils;
import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.*;

@Path(value = "/comparison")
public class ComparisonResources extends Resources {
    private static final Logger logger = LoggerFactory.getLogger(ComparisonResources.class);

    private ComparisonService comparisonService;

    /**
     * Constructor
     *
     * @param globalConfiguration global application configuration
     * @throws UnknownHostException
     */
    public ComparisonResources(GlobalConfiguration globalConfiguration) throws UnknownHostException {
        super(globalConfiguration);
        comparisonService = new ComparisonServiceImpl(globalConfiguration);
        // set GroupDocs license
        try {
            License license = new License();
            license.setLicense(globalConfiguration.getApplication().getLicensePath());
        } catch (Throwable exc) {
            logger.error("Can not verify Comparison license!");
        }
    }

    /**
     * Get comparison page
     * @return template name
     */
    @GET
    public Comparison getView() {
        logger.debug("comparison config: {}", globalConfiguration.getComparison());
        // initiate index page
        return new Comparison(globalConfiguration, DEFAULT_CHARSET);
    }

    /**
     * Get files and directories
     * @return files and directories list
     */
    @POST
    @Path(value = "/loadFileTree")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public List<FileDescriptionEntity> loadFileTree(FileTreeRequest fileTreeRequest){
        return comparisonService.loadFiles(fileTreeRequest);
    }

    /**
     * Download results
     *
     * @param documentGuid unique key of results
     * @param index page number of result images
     * @param ext results file extension
     */
    @GET
    @Path(value = "/downloadDocument")
    @Produces(APPLICATION_OCTET_STREAM)
    public void downloadDocument(@QueryParam("guid") String documentGuid,
                                 @QueryParam("index") Integer index,
                                 @QueryParam("ext") String ext,
                                 @Context HttpServletResponse response) {
        String filePath = comparisonService.calculateResultFileName(documentGuid, index, ext);
        downloadFile(response, filePath);
    }

    /**
     * Upload document
     * @return uploaded document object (the object contains uploaded document guid)
     */
    @POST
    @Path(value = "/uploadDocument")
    @Produces(APPLICATION_JSON)
    @Consumes(MULTIPART_FORM_DATA)
    public UploadedDocumentEntity uploadDocument(@FormDataParam("file")  InputStream inputStream,
                                                 @FormDataParam("file") FormDataContentDisposition fileDetail,
                                                 @FormDataParam("url") String url,
                                                 @FormDataParam("rewrite") Boolean rewrite) {
        // upload file
        String pathname = uploadFile(url, inputStream, fileDetail, rewrite, null);
        // create response
        UploadedDocumentEntity uploadedDocument = new UploadedDocumentEntity();
        uploadedDocument.setGuid(pathname);
        return uploadedDocument;
    }

    @Override
    protected String getStoragePath(Map<String, Object> params) {
        return globalConfiguration.getComparison().getFilesDirectory();
    }

    /**
     * Compare files from local storage
     *
     * @param compareRequest request with paths to files
     * @return response with compare results
     */
    @POST
    @Path(value = "/compareWithPaths")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public CompareResultResponse compareWithPaths(CompareRequest compareRequest) {
        // check formats
        if (comparisonService.checkFiles(compareRequest.getFirstPath(), compareRequest.getSecondPath())) {
            // compare
            return comparisonService.compare(compareRequest);
        } else {
            logger.error("Document types are different");
            throw new TotalGroupDocsException("Document types are different");
        }
    }

    /**
     * Compare documents from form formats
     *
     * @param firstFileDetail details of first file
     * @param firstInputStream content of first file
     * @param secondFileDetail details of second file
     * @param secondInputStream content of second file
     * @param firstPassword password for first file
     * @param secondPassword password for second file
     * @return response with compare results
     */
    @POST
    @Path(value = "/compareFiles")
    @Produces(APPLICATION_JSON)
    @Consumes(MULTIPART_FORM_DATA)
    public CompareResultResponse compareFiles(@FormDataParam("firstFile")  InputStream firstInputStream,
                                              @FormDataParam("firstFile") FormDataContentDisposition firstFileDetail,
                                              @FormDataParam("secondFile")  InputStream secondInputStream,
                                              @FormDataParam("secondFile") FormDataContentDisposition secondFileDetail,
                                              @FormDataParam("firstPassword") String firstPassword,
                                              @FormDataParam("secondPassword") String secondPassword) {
        String firstFileName = firstFileDetail.getFileName();
        String secondFileName = secondFileDetail.getFileName();
        // check formats
        if (comparisonService.checkFiles(firstFileName, secondFileName)) {
            // compare files
            return comparisonService.compareFiles(firstInputStream, firstPassword, secondInputStream, secondPassword, FilenameUtils.getExtension(firstFileName));
        } else {
            logger.error("Document types are different");
            throw new TotalGroupDocsException("Document types are different");
        }
    }

    /**
     * Compare two files by urls
     *
     * @param compareRequest request with urls to files
     * @return response with compare results
     */
    @POST
    @Path(value = "/compareWithUrls")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public CompareResultResponse compareWithUrls(CompareRequest compareRequest) {
        try {
            String firstPath = compareRequest.getFirstPath();
            String secondPath = compareRequest.getSecondPath();
            // check formats
            if (comparisonService.checkFiles(firstPath, secondPath)) {
                URL fUrl = URI.create(firstPath).toURL();
                URL sUrl = URI.create(secondPath).toURL();

                String firstPassword = compareRequest.getFirstPassword();
                String secondPassword = compareRequest.getSecondPassword();
                // open streams for urls
                try (InputStream firstContent = fUrl.openStream();
                     InputStream secondContent = sUrl.openStream()) {
                    // compare
                    return comparisonService.compareFiles(firstContent, firstPassword, secondContent, secondPassword, FilenameUtils.getExtension(firstPath));
                }
            } else {
                logger.error("Document types are different");
                throw new TotalGroupDocsException("Document types are different");
            }
        } catch (IOException e) {
            logger.error("Exception occurred while compare files by urls.");
            throw new TotalGroupDocsException("Exception occurred while compare files by urls.", e);
        }
    }

    /**
     * Get result page
     * @return result page image
     */
    @POST
    @Path(value = "/loadResultPage")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public PageDescriptionEntity loadResultPage(LoadResultPageRequest loadResultPageRequest){
        return comparisonService.loadResultPage(loadResultPageRequest);
    }

    /**
     * Compare 2 files got by different ways
     *
     * @param files files data
     * @param fileDetails files details
     * @param passwords files passwords
     * @param urls files url and password
     * @param paths files path and password
     * @return response with compare results
     */
    @POST
    @Path(value = "/compare")
    @Produces(APPLICATION_JSON)
    @Consumes(MULTIPART_FORM_DATA)
    public CompareResultResponse compare(@FormDataParam("files") List<FormDataBodyPart> files,
                                         @FormDataParam("files") FormDataContentDisposition fileDetails,
                                         @FormDataParam("passwords") List<String> passwords,
                                         @FormDataParam("urls") List<CompareFileDataRequest> urls,
                                         @FormDataParam("paths") List<CompareFileDataRequest> paths) {
        // check if there is no files
        if (files == null) {
            files = Collections.EMPTY_LIST;
        }
        // calculate total amount of files
        int initialCapacity = files.size() + urls.size() + paths.size();

        if (initialCapacity != 2) {
            throw new TotalGroupDocsException("Comparing is impossible. There are must be 2 files.");
        }
        try {
            // transform all files into input streams
            TransformFiles transformFiles = new TransformFiles(files, passwords, urls, paths, initialCapacity).transformToStreams();
            List<String> fileNames = transformFiles.getFileNames();

            // check formats
            if (comparisonService.checkMultiFiles(fileNames)) {
                // get file extension
                String ext = FilenameUtils.getExtension(fileNames.get(0));

                // compare
                List<InputStream> newFiles = transformFiles.getNewFiles();
                List<String> newPasswords = transformFiles.getNewPasswords();
                return comparisonService.compareFiles(newFiles.get(0), newPasswords.get(0), newFiles.get(1), newPasswords.get(1), ext);
            } else {
                logger.error("Document types are different");
                throw new TotalGroupDocsException("Document types are different");
            }
        } catch (IOException e) {
            logger.error("Exception occurred while compare files.");
            throw new TotalGroupDocsException("Exception occurred while compare files.", e);
        }
    }

    /**
     * Compare several files got by different ways
     *
     * @param files files data
     * @param fileDetails files details
     * @param passwords files passwords
     * @param urls files url and password
     * @param paths files path and password
     * @return response with compare results
     */
    @POST
    @Path(value = "/multiCompare")
    @Produces(APPLICATION_JSON)
    @Consumes(MULTIPART_FORM_DATA)
    public CompareResultResponse multiCompare(@FormDataParam("files") List<FormDataBodyPart> files,
                                              @FormDataParam("files") FormDataContentDisposition fileDetails,
                                              @FormDataParam("passwords") List<String> passwords,
                                              @FormDataParam("urls") List<CompareFileDataRequest> urls,
                                              @FormDataParam("paths") List<CompareFileDataRequest> paths) {
        // check if there is no files
        if (files == null) {
            files = Collections.EMPTY_LIST;
        }
        // calculate total amount of files
        int initialCapacity = files.size() + urls.size() + paths.size();

        if (initialCapacity < 2) {
            throw new TotalGroupDocsException("Comparing is impossible. There are less than 2 files.");
        }
        try {
            // transform all files into input streams
            TransformFiles transformFiles = new TransformFiles(files, passwords, urls, paths, initialCapacity).transformToStreams();
            List<String> fileNames = transformFiles.getFileNames();

            // check formats
            if (comparisonService.checkMultiFiles(fileNames)) {
                // get file extension
                String ext = FilenameUtils.getExtension(fileNames.get(0));

                // compare
                return comparisonService.multiCompareFiles(transformFiles.getNewFiles(), transformFiles.getNewPasswords(), ext);
            } else {
                logger.error("Document types are different");
                throw new TotalGroupDocsException("Document types are different");
            }
        } catch (IOException e) {
            logger.error("Exception occurred while multi compare files by streams.");
            throw new TotalGroupDocsException("Exception occurred while multi compare files by streams.", e);
        }
    }

    private class TransformFiles {
        private List<FormDataBodyPart> files;
        private List<String> passwords;
        private List<CompareFileDataRequest> urls;
        private List<CompareFileDataRequest> paths;
        private int initialCapacity;
        private List<InputStream> newFiles;
        private List<String> fileNames;
        private List<String> newPasswords;

        public TransformFiles(List<FormDataBodyPart> files, List<String> passwords, List<CompareFileDataRequest> urls, List<CompareFileDataRequest> paths, int initialCapacity) {
            this.files = files;
            this.passwords = passwords;
            this.urls = urls;
            this.paths = paths;
            this.initialCapacity = initialCapacity;
        }

        public List<InputStream> getNewFiles() {
            return newFiles;
        }

        public List<String> getFileNames() {
            return fileNames;
        }

        public List<String> getNewPasswords() {
            return newPasswords;
        }

        public TransformFiles transformToStreams() throws IOException {
            newFiles = new ArrayList<>(initialCapacity);
            fileNames = new ArrayList<>(initialCapacity);
            newPasswords = new ArrayList<>(initialCapacity);

            // transform files
            int index = 0;
            for (FormDataBodyPart file: files) {
                fileNames.add(file.getContentDisposition().getFileName());
                newFiles.add(((BodyPartEntity) file.getEntity()).getInputStream());
                newPasswords.add(passwords.get(index));
                index++;
            }

            // transform urls
            for (CompareFileDataRequest urlRequest: urls) {
                String file = urlRequest.getFile();
                fileNames.add(file);
                URL url = URI.create(file).toURL();
                newFiles.add(url.openStream());
                newPasswords.add(urlRequest.getPassword());
            }

            // transform paths
            for (CompareFileDataRequest pathRequest: paths) {
                String file = pathRequest.getFile();
                fileNames.add(file);
                newFiles.add(new FileInputStream(file));
                newPasswords.add(pathRequest.getPassword());
            }
            return this;
        }
    }
}
