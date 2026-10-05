package com.sainagesh.bank.testing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The architecture rules every service follows, written as checks a test can run.
 *
 * <p>Dependencies only point inward. The domain is plain Java, use cases know the domain and their
 * ports, and adapters sit on the outside. If someone adds a Spring annotation to a domain class, or
 * calls a database class from a controller, the build fails and says which line did it.
 */
public final class HexagonalRules {

    private final JavaClasses classes;

    private HexagonalRules(String basePackage) {
        this.classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
    }

    /** @param basePackage the root package of one service, such as {@code com.sainagesh.bank.ledger} */
    public static HexagonalRules of(String basePackage) {
        return new HexagonalRules(basePackage);
    }

    /** The business rules must run with nothing but the JDK. */
    public void domainIsPlainJava() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "org.hibernate..",
                        "org.apache.kafka..",
                        "tools.jackson..",
                        "com.fasterxml..",
                        "org.slf4j..")
                .because("the domain holds business rules only, so they can be tested in milliseconds")
                .check(classes);
    }

    public void domainDoesNotKnowTheOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..application..", "..adapter..", "..config..")
                .because("dependencies point inward")
                .check(classes);
    }

    public void useCasesDoNotKnowTheAdapters() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..adapter..")
                .because("a use case talks to the outside through a port, never to an adapter")
                .check(classes);
    }

    public void useCasesDoNotKnowWebOrDatabaseTypes() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "jakarta.servlet..", "org.apache.kafka..")
                .because("how data is stored or sent is an adapter's business")
                .check(classes);
    }

    public void inboundAndOutboundAdaptersAreStrangers() {
        noClasses()
                .that()
                .resideInAPackage("..adapter.in..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter.out..")
                .because("a controller reaches the database only through a use case")
                .check(classes);
        noClasses()
                .that()
                .resideInAPackage("..adapter.out..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter.in..")
                .check(classes);
    }

    /** Runs every rule. */
    public void checkAll() {
        domainIsPlainJava();
        domainDoesNotKnowTheOuterLayers();
        useCasesDoNotKnowTheAdapters();
        useCasesDoNotKnowWebOrDatabaseTypes();
        inboundAndOutboundAdaptersAreStrangers();
    }
}
