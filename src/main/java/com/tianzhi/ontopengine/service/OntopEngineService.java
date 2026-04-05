package com.tianzhi.ontopengine.service;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Injector;
import com.tianzhi.ontopengine.model.BootstrapRequest;
import com.tianzhi.ontopengine.model.BootstrapResponse;
import com.tianzhi.ontopengine.model.ExtractMetadataRequest;
import com.tianzhi.ontopengine.model.ExtractMetadataResponse;
import com.tianzhi.ontopengine.model.JdbcConfig;
import com.tianzhi.ontopengine.model.ValidateRequest;
import com.tianzhi.ontopengine.model.ValidateResponse;
import it.unibz.inf.ontop.exception.OBDASpecificationException;
import it.unibz.inf.ontop.injection.OntopMappingSQLConfiguration;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.spec.dbschema.tools.DBMetadataExtractorAndSerializer;
import it.unibz.inf.ontop.spec.mapping.bootstrap.DirectMappingBootstrapper;
import it.unibz.inf.ontop.spec.mapping.serializer.impl.OntopNativeMappingSerializer;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLNamedObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Service
public class OntopEngineService {

    public ExtractMetadataResponse extractMetadata(ExtractMetadataRequest request) {
        ExtractMetadataResponse response = new ExtractMetadataResponse();
        try {
            OntopMappingSQLConfiguration configuration = mappingConfiguration(request.getJdbc()).build();
            Injector injector = configuration.getInjector();
            DBMetadataExtractorAndSerializer extractor = injector.getInstance(DBMetadataExtractorAndSerializer.class);
            response.setSuccess(true);
            response.setMetadataJson(extractor.extractAndSerialize());
            response.setMessage("Metadata extraction completed");
            return response;
        }
        catch (Exception e) {
            response.setSuccess(false);
            response.setMessage(stackTrace(e));
            return response;
        }
    }

    public BootstrapResponse bootstrap(BootstrapRequest request) {
        BootstrapResponse response = new BootstrapResponse();
        Path mappingFile = null;
        try {
            OntopSQLOWLAPIConfiguration configuration = owlConfiguration(request.getJdbc()).build();
            DirectMappingBootstrapper.BootstrappingResults results =
                    DirectMappingBootstrapper.defaultBootstrapper().bootstrap(configuration, request.getBaseIri());

            mappingFile = Files.createTempFile("ontop-bootstrap-", ".obda");
            OntopNativeMappingSerializer serializer = new OntopNativeMappingSerializer();
            serializer.write(mappingFile.toFile(), results.getPPMapping());

            ByteArrayOutputStream ontologyOut = new ByteArrayOutputStream();
            results.getOntology().getOWLOntologyManager().saveOntology(
                    results.getOntology(),
                    new TurtleDocumentFormat(),
                    ontologyOut
            );

            response.setSuccess(true);
            response.setMapping(Files.readString(mappingFile, StandardCharsets.UTF_8));
            response.setOntology(ontologyOut.toString(StandardCharsets.UTF_8));
            response.setMessage("Bootstrap completed");
            return response;
        }
        catch (Exception e) {
            response.setSuccess(false);
            response.setMessage(stackTrace(e));
            return response;
        }
        finally {
            deleteIfExists(mappingFile);
        }
    }

    public ValidateResponse validate(ValidateRequest request) {
        ValidateResponse response = new ValidateResponse();
        Path mappingFile = null;
        Path ontologyFile = null;
        try {
            mappingFile = Files.createTempFile("ontop-", ".obda");
            ontologyFile = Files.createTempFile("ontop-", ".ttl");
            Files.writeString(mappingFile, request.getMappingContent(), StandardCharsets.UTF_8);
            Files.writeString(ontologyFile, request.getOntologyContent(), StandardCharsets.UTF_8);

            OntopSQLOWLAPIConfiguration configuration = owlConfiguration(request.getJdbc())
                    .ontologyFile(ontologyFile.toString())
                    .nativeOntopMappingFile(mappingFile.toString())
                    .build();

            OWLOntology ontology = configuration.loadProvidedInputOntology();
            validatePunning(ontology);
            configuration.loadSpecification();

            response.setSuccess(true);
            response.setMessage("Validation completed");
            return response;
        }
        catch (Exception e) {
            response.setSuccess(false);
            response.setMessage(stackTrace(e));
            return response;
        }
        finally {
            deleteIfExists(mappingFile);
            deleteIfExists(ontologyFile);
        }
    }

    private OntopMappingSQLConfiguration.Builder<?> mappingConfiguration(JdbcConfig jdbc) {
        return OntopMappingSQLConfiguration.defaultBuilder()
                .jdbcUrl(jdbc.getJdbcUrl())
                .jdbcUser(jdbc.getUser())
                .jdbcPassword(jdbc.getPassword())
                .jdbcDriver(jdbc.getDriver());
    }

    private OntopSQLOWLAPIConfiguration.Builder<?> owlConfiguration(JdbcConfig jdbc) {
        return OntopSQLOWLAPIConfiguration.defaultBuilder()
                .jdbcUrl(jdbc.getJdbcUrl())
                .jdbcUser(jdbc.getUser())
                .jdbcPassword(jdbc.getPassword())
                .jdbcDriver(jdbc.getDriver());
    }

    private void validatePunning(OWLOntology ontology) throws OBDASpecificationException, OWLOntologyCreationException {
        Set<IRI> classIris = ontology.getClassesInSignature().stream()
                .map(OWLNamedObject::getIRI)
                .collect(toSet());
        Set<IRI> objectPropertyIris = ontology.getObjectPropertiesInSignature().stream()
                .map(OWLNamedObject::getIRI)
                .collect(toSet());
        Set<IRI> dataPropertyIris = ontology.getDataPropertiesInSignature().stream()
                .map(OWLNamedObject::getIRI)
                .collect(toSet());

        ImmutableSet<IRI> classObjectIntersections = Sets.intersection(classIris, objectPropertyIris).immutableCopy();
        ImmutableSet<IRI> classDataIntersections = Sets.intersection(classIris, dataPropertyIris).immutableCopy();
        ImmutableSet<IRI> objectDataIntersections = Sets.intersection(objectPropertyIris, dataPropertyIris).immutableCopy();

        if (!classObjectIntersections.isEmpty() || !classDataIntersections.isEmpty() || !objectDataIntersections.isEmpty()) {
            throw new IllegalArgumentException("Ontology contains punning conflicts");
        }
    }

    private String stackTrace(Exception e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        e.printStackTrace(writer);
        writer.flush();
        return out.toString(StandardCharsets.UTF_8);
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored) {
        }
    }
}
