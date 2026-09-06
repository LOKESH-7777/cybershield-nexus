package com.cybershield.config;

import com.cybershield.model.*;
import com.cybershield.model.AssetRelationship.AssetType;
import com.cybershield.model.AssetRelationship.RelationshipType;
import com.cybershield.model.DigitalService.ServiceStatus;
import com.cybershield.model.DigitalService.ServiceType;
import com.cybershield.model.Institution.InstitutionStatus;
import com.cybershield.model.Institution.InstitutionType;
import com.cybershield.model.Server.ServerStatus;
import com.cybershield.model.Firewall.FirewallStatus;
import com.cybershield.model.License.LicenseStatus;
import com.cybershield.model.License.LicenseType;
import com.cybershield.model.Hardware.HardwareStatus;
import com.cybershield.model.Hardware.HardwareType;
import com.cybershield.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * AssetDataSeeder — seeds all NEDI infrastructure assets.
 *
 * NEDI Demo Story:
 *   The 'viewer' account (low-privilege student portal user) is compromised.
 *   Attacker pivots: viewer → NEDI-DMZ-FW-01 → NEDI-EXAM-WEB-01
 *                            → NEDI-EXAM-APP-01 → NEDI-EXAM-DB-01
 *
 *   NEDI-EXAM-APP-01 is intentionally UNPATCHED (120 days).
 *   NEDI-EXAM-DB-01 has an EXPIRED Oracle license → risk score 55+.
 *   This is what BFS finds and Risk Engine flags.
 *
 * Runs AFTER DataInitializer (Order 2).
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AssetDataSeeder implements CommandLineRunner {

    private final ServerRepository              serverRepository;
    private final FirewallRepository            firewallRepository;
    private final LicenseRepository             licenseRepository;
    private final HardwareRepository            hardwareRepository;
    private final AssetRelationshipRepository   relationshipRepository;
    private final DigitalServiceRepository      digitalServiceRepository;
    private final InstitutionRepository         institutionRepository;

    @Override
    public void run(String... args) {
        // Detect old pre-Phase2 generic names — wipe and re-seed with NEDI data
        boolean hasOldData = serverRepository.existsByNameContaining("Server-Web-01")
                || serverRepository.existsByNameContaining("Server-App-02")
                || serverRepository.existsByNameContaining("Server-DB-03");

        if (hasOldData) {
            log.warn("Old generic server data detected — wiping and re-seeding with NEDI data...");
            relationshipRepository.deleteAll();
            licenseRepository.deleteAll();
            hardwareRepository.deleteAll();
            firewallRepository.deleteAll();
            serverRepository.deleteAll();
            digitalServiceRepository.deleteAll();
            institutionRepository.deleteAll();
        }

        if (serverRepository.count() == 0) {
            seedServers();
            seedFirewalls();
            seedLicenses();
            seedHardware();
            log.info("NEDI asset seed data loaded successfully");
        } else {
            log.debug("NEDI assets already seeded — skipping asset seed");
        }

        if (digitalServiceRepository.count() == 0) {
            seedDigitalServices();
        } else {
            log.debug("Digital services already seeded — skipping");
        }

        if (institutionRepository.count() == 0) {
            seedInstitutions();
        } else {
            log.debug("Institutions already seeded — skipping");
        }

        seedGraph();
    }

    // ─────────────────────────────────────────────────────────────────
    // SERVERS — NEDI infrastructure naming convention
    // ─────────────────────────────────────────────────────────────────
    private void seedServers() {

        // NEDI Student Portal Web Server — recently patched (low risk)
        serverRepository.save(Server.builder()
                .name("NEDI-STUDENT-WEB-01").ipAddress("10.0.1.10")
                .operatingSystem("Ubuntu").osVersion("22.04 LTS")
                .status(ServerStatus.RUNNING).location("DMZ Rack A1")
                .owner("viewer")
                .serviceCode("STUDENT_PORTAL")
                .cpuCores(8).ramGb(32).storageGb(500)
                .lastPatchDate(LocalDate.now().minusDays(10))
                .warrantyExpiry(LocalDate.now().plusYears(2))
                .notes("Student Portal frontend — recently patched, low risk").build());

        // NEDI Exam Portal App Server — UNPATCHED 120 days (HIGH risk +30)
        serverRepository.save(Server.builder()
                .name("NEDI-EXAM-APP-01").ipAddress("10.0.1.11")
                .operatingSystem("CentOS").osVersion("7.9")
                .status(ServerStatus.RUNNING).location("Core Rack A2")
                .owner("serveradmin")
                .serviceCode("EXAM_PORTAL")
                .cpuCores(16).ramGb(64).storageGb(1000)
                .lastPatchDate(LocalDate.now().minusDays(120))
                .warrantyExpiry(LocalDate.now().plusYears(1))
                .notes("Exam Portal application server — UNPATCHED 120 DAYS — HIGH RISK").build());

        // NEDI Exam Portal DB Server — UNPATCHED + EXPIRED LICENSE (CRITICAL risk 55+)
        serverRepository.save(Server.builder()
                .name("NEDI-EXAM-DB-01").ipAddress("10.0.1.12")
                .operatingSystem("Windows Server").osVersion("2019")
                .status(ServerStatus.RUNNING).location("DB Rack A3")
                .owner("admin")
                .serviceCode("EXAM_PORTAL")
                .cpuCores(32).ramGb(128).storageGb(5000)
                .lastPatchDate(LocalDate.now().minusDays(95))
                .warrantyExpiry(LocalDate.now().plusMonths(3))
                .notes("Exam Portal database — HIGH VALUE TARGET — unpatched + expired license").build());

        // NEDI Faculty Portal Server — healthy
        serverRepository.save(Server.builder()
                .name("NEDI-FACULTY-WEB-01").ipAddress("10.0.1.13")
                .operatingSystem("Ubuntu").osVersion("22.04 LTS")
                .status(ServerStatus.RUNNING).location("Core Rack B1")
                .owner("serveradmin")
                .serviceCode("FACULTY_PORTAL")
                .cpuCores(8).ramGb(32).storageGb(500)
                .lastPatchDate(LocalDate.now().minusDays(30))
                .warrantyExpiry(LocalDate.now().plusYears(2))
                .notes("Faculty portal — healthy, recently patched").build());

        // NEDI ERP Server — in maintenance
        serverRepository.save(Server.builder()
                .name("NEDI-ERP-APP-01").ipAddress("10.0.1.14")
                .operatingSystem("Red Hat Enterprise Linux").osVersion("8.6")
                .status(ServerStatus.MAINTENANCE).location("Core Rack B2")
                .owner("serveradmin")
                .serviceCode("ERP")
                .cpuCores(16).ramGb(64).storageGb(2000)
                .lastPatchDate(LocalDate.now().minusDays(5))
                .warrantyExpiry(LocalDate.now().plusYears(3))
                .notes("ERP server — under scheduled maintenance").build());

        log.info("Seeded 5 NEDI servers (Exam Portal at HIGH RISK)");
    }

    // ─────────────────────────────────────────────────────────────────
    // FIREWALLS — NEDI naming convention
    // ─────────────────────────────────────────────────────────────────
    private void seedFirewalls() {

        // NEDI DMZ Edge Firewall — outdated firmware (attack entry point)
        firewallRepository.save(Firewall.builder()
                .name("NEDI-DMZ-FW-01").vendor("Cisco").model("ASA 5505")
                .ipAddress("10.0.0.1").firmwareVersion("9.8.4")
                .status(FirewallStatus.ACTIVE).location("DMZ Rack")
                .activeRulesCount(142)
                .lastRuleReviewDate(LocalDate.now().minusDays(45))
                .lastFirmwareUpdate(LocalDate.now().minusDays(200))
                .warrantyExpiry(LocalDate.now().plusYears(1))
                .notes("Primary DMZ edge firewall — OUTDATED FIRMWARE 200 days").build());

        // NEDI Core Internal Firewall — well maintained
        firewallRepository.save(Firewall.builder()
                .name("NEDI-CORE-FW-01").vendor("Palo Alto").model("PA-220")
                .ipAddress("10.0.1.1").firmwareVersion("10.2.3")
                .status(FirewallStatus.ACTIVE).location("Core Rack")
                .activeRulesCount(89)
                .lastRuleReviewDate(LocalDate.now().minusDays(15))
                .lastFirmwareUpdate(LocalDate.now().minusDays(20))
                .warrantyExpiry(LocalDate.now().plusYears(2))
                .notes("Core internal firewall — up to date").build());

        // NEDI DB Zone Firewall — protects exam database
        firewallRepository.save(Firewall.builder()
                .name("NEDI-DB-FW-01").vendor("Fortinet").model("FortiGate 60F")
                .ipAddress("10.0.1.50").firmwareVersion("7.2.1")
                .status(FirewallStatus.ACTIVE).location("DB Rack")
                .activeRulesCount(55)
                .lastRuleReviewDate(LocalDate.now().minusDays(7))
                .lastFirmwareUpdate(LocalDate.now().minusDays(30))
                .warrantyExpiry(LocalDate.now().plusYears(3))
                .notes("Database zone firewall — protects Exam DB").build());

        log.info("Seeded 3 NEDI firewalls (DMZ-FW-01 has outdated firmware)");
    }

    // ─────────────────────────────────────────────────────────────────
    // LICENSES — tied to NEDI services
    // ─────────────────────────────────────────────────────────────────
    private void seedLicenses() {

        // Fetch servers seeded above
        var servers = serverRepository.findAll();
        Server studentWeb = servers.stream().filter(s -> s.getName().contains("STUDENT-WEB")).findFirst().orElse(null);
        Server examApp    = servers.stream().filter(s -> s.getName().contains("EXAM-APP")).findFirst().orElse(null);
        Server examDb     = servers.stream().filter(s -> s.getName().contains("EXAM-DB")).findFirst().orElse(null);

        // EXPIRED — Oracle license on Exam DB server (triggers risk +25 on NEDI-EXAM-DB-01)
        licenseRepository.save(License.builder()
                .softwareName("Oracle Database 19c").vendor("Oracle")
                .licenseType(LicenseType.PER_SEAT).totalSeats(5).usedSeats(5)
                .expiryDate(LocalDate.now().minusDays(30))
                .purchaseDate(LocalDate.now().minusYears(3))
                .renewalCost(45000.00).assignedTo("NEDI-EXAM-DB-01")
                .server(examDb)
                .status(LicenseStatus.EXPIRED)
                .notes("CRITICAL: Expired Oracle license on Exam DB — compliance violation").build());

        // EXPIRING SOON — Windows license on Student Web server
        licenseRepository.save(License.builder()
                .softwareName("Windows Server 2022").vendor("Microsoft")
                .licenseType(LicenseType.PER_SEAT).totalSeats(10).usedSeats(7)
                .expiryDate(LocalDate.now().plusDays(15))
                .purchaseDate(LocalDate.now().minusYears(1))
                .renewalCost(12000.00).assignedTo("NEDI-STUDENT-WEB-01")
                .server(studentWeb)
                .status(LicenseStatus.PENDING_RENEWAL)
                .notes("Student Portal web license expiring in 15 days — renew immediately").build());

        // ACTIVE — CrowdStrike endpoint protection for all servers
        licenseRepository.save(License.builder()
                .softwareName("CrowdStrike Falcon EDR").vendor("CrowdStrike")
                .licenseType(LicenseType.SUBSCRIPTION).totalSeats(50).usedSeats(23)
                .expiryDate(LocalDate.now().plusYears(1))
                .purchaseDate(LocalDate.now().minusMonths(3))
                .renewalCost(8500.00).assignedTo("All NEDI Servers")
                .status(LicenseStatus.ACTIVE)
                .notes("Endpoint security for all NEDI infrastructure — active and healthy").build());

        // ACTIVE — RHEL for Exam App server
        licenseRepository.save(License.builder()
                .softwareName("Red Hat Enterprise Linux").vendor("Red Hat")
                .licenseType(LicenseType.SUBSCRIPTION).totalSeats(5).usedSeats(3)
                .expiryDate(LocalDate.now().plusMonths(8))
                .purchaseDate(LocalDate.now().minusMonths(4))
                .renewalCost(6000.00).assignedTo("NEDI-EXAM-APP-01")
                .server(examApp)
                .status(LicenseStatus.ACTIVE)
                .notes("RHEL subscription for Exam Portal app server").build());

        log.info("Seeded 4 licenses (Oracle EXPIRED on Exam DB = risk +25)");
    }

    // ─────────────────────────────────────────────────────────────────
    // HARDWARE — NEDI infrastructure asset tags
    // ─────────────────────────────────────────────────────────────────
    private void seedHardware() {

        hardwareRepository.save(Hardware.builder()
                .assetTag("NEDI-SW-001").name("Core Switch Layer-3")
                .hardwareType(HardwareType.SWITCH).manufacturer("Cisco")
                .model("Catalyst 3750").serialNumber("CAT3750-001")
                .location("Core Rack, Slot 1").status(HardwareStatus.IN_USE)
                .purchaseDate(LocalDate.now().minusYears(3))
                .warrantyExpiry(LocalDate.now().plusMonths(6))
                .lastMaintenanceDate(LocalDate.now().minusDays(60))
                .nextMaintenanceDate(LocalDate.now().plusDays(30))
                .notes("Primary layer-3 switch — connects all NEDI VLANs").build());

        hardwareRepository.save(Hardware.builder()
                .assetTag("NEDI-LB-001").name("NEDI Load Balancer Primary")
                .hardwareType(HardwareType.LOAD_BALANCER).manufacturer("F5")
                .model("BIG-IP 2000s").serialNumber("F5-LB-001")
                .location("DMZ Rack, Slot 3").status(HardwareStatus.IN_USE)
                .purchaseDate(LocalDate.now().minusYears(2))
                .warrantyExpiry(LocalDate.now().plusYears(1))
                .lastMaintenanceDate(LocalDate.now().minusDays(30))
                .nextMaintenanceDate(LocalDate.now().plusDays(60))
                .notes("Distributes traffic across Student and Exam portal web servers").build());

        hardwareRepository.save(Hardware.builder()
                .assetTag("NEDI-UPS-001").name("UPS Unit A — DB Rack")
                .hardwareType(HardwareType.UPS).manufacturer("APC")
                .model("Smart-UPS 3000").serialNumber("APC-UPS-001")
                .location("DB Rack A, Power Unit").status(HardwareStatus.IN_USE)
                .purchaseDate(LocalDate.now().minusYears(4))
                .warrantyExpiry(LocalDate.now().minusDays(10))
                .lastMaintenanceDate(LocalDate.now().minusDays(90))
                .nextMaintenanceDate(LocalDate.now().plusDays(0))
                .notes("UPS protecting Exam DB rack — WARRANTY EXPIRED, maintenance overdue").build());

        log.info("Seeded 3 NEDI hardware assets");
    }

    // ─────────────────────────────────────────────────────────────────
    // DIGITAL SERVICES — 8 NEDI services
    // ─────────────────────────────────────────────────────────────────
    private void seedDigitalServices() {

        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("STUDENT_PORTAL").name("Student Portal")
                .serviceType(ServiceType.STUDENT_PORTAL)
                .description("Student profile, fee payment, academic records, and attendance")
                .hostDomain("student.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.HEALTHY)
                .criticalityLevel(4).build());

        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("FACULTY_PORTAL").name("Faculty Portal")
                .serviceType(ServiceType.FACULTY_PORTAL)
                .description("Faculty attendance, course management, and evaluation workflow")
                .hostDomain("faculty.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.HEALTHY)
                .criticalityLevel(3).build());

        // EXAM PORTAL — intentionally HIGH_RISK for the demo scenario
        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("EXAM_PORTAL").name("Exam Portal")
                .serviceType(ServiceType.EXAM_PORTAL)
                .description("National examination system — high-impact, used by 500+ institutions")
                .hostDomain("exam.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.HIGH_RISK)
                .criticalityLevel(5).build());

        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("ERP").name("ERP System")
                .serviceType(ServiceType.ERP)
                .description("Finance, HR, operations, and administrative management")
                .hostDomain("erp.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.DEGRADED)
                .criticalityLevel(4).build());

        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("ADMISSION_PORTAL").name("Admission Portal")
                .serviceType(ServiceType.ADMISSION_PORTAL)
                .description("Student admission applications and document verification")
                .hostDomain("admission.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.HEALTHY)
                .criticalityLevel(3).build());

        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("DIGITAL_LIBRARY").name("Digital Library")
                .serviceType(ServiceType.DIGITAL_LIBRARY)
                .description("E-books, research papers, and digital resource access")
                .hostDomain("library.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.HEALTHY)
                .criticalityLevel(2).build());

        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("LMS").name("Learning Management System")
                .serviceType(ServiceType.LMS)
                .description("Online classes, assignments, quizzes, and course submissions")
                .hostDomain("lms.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.HEALTHY)
                .criticalityLevel(4).build());

        digitalServiceRepository.save(DigitalService.builder()
                .serviceCode("MAIL_SERVICE").name("Institutional Mail")
                .serviceType(ServiceType.MAIL_SERVICE)
                .description("Institutional email and identity-linked communication service")
                .hostDomain("mail.nedi.local")
                .institutionCode("NEDI-CENTRAL")
                .status(ServiceStatus.HEALTHY)
                .criticalityLevel(3).build());

        log.info("Seeded 8 NEDI digital services (Exam Portal = HIGH_RISK, ERP = DEGRADED)");
    }

    // ─────────────────────────────────────────────────────────────────
    // INSTITUTIONS — representative NEDI monitored organizations
    // ─────────────────────────────────────────────────────────────────
    private void seedInstitutions() {

        institutionRepository.save(Institution.builder()
                .institutionCode("NEDI-CENTRAL")
                .name("NEDI Central Operations")
                .type(InstitutionType.CENTRAL_OPERATIONS)
                .city("New Delhi")
                .state("Delhi")
                .region("National")
                .status(InstitutionStatus.ACTIVE)
                .studentCount(0)
                .servicesMonitored(8)
                .notes("Central SOC and digital infrastructure operations unit for the fictional NEDI scenario")
                .build());

        institutionRepository.save(Institution.builder()
                .institutionCode("COL-A-SOUTH")
                .name("College A - South Campus")
                .type(InstitutionType.ENGINEERING_COLLEGE)
                .city("Chennai")
                .state("Tamil Nadu")
                .region("South")
                .status(InstitutionStatus.ACTIVE)
                .studentCount(8400)
                .servicesMonitored(8)
                .notes("Representative institution using Student, Faculty, Exam, ERP, LMS, Library, Admission and Mail services")
                .build());

        institutionRepository.save(Institution.builder()
                .institutionCode("COL-B-WEST")
                .name("College B - Western Institute")
                .type(InstitutionType.ENGINEERING_COLLEGE)
                .city("Pune")
                .state("Maharashtra")
                .region("West")
                .status(InstitutionStatus.ACTIVE)
                .studentCount(6200)
                .servicesMonitored(8)
                .notes("Healthy institution in the NEDI monitoring scenario")
                .build());

        institutionRepository.save(Institution.builder()
                .institutionCode("UNI-C-NORTH")
                .name("University C - North Cluster")
                .type(InstitutionType.UNIVERSITY)
                .city("Jaipur")
                .state("Rajasthan")
                .region("North")
                .status(InstitutionStatus.DEGRADED)
                .studentCount(18500)
                .servicesMonitored(8)
                .notes("Representative degraded institution affected by Exam Portal latency during the demo")
                .build());

        institutionRepository.save(Institution.builder()
                .institutionCode("POLY-D-EAST")
                .name("Polytechnic D - East Campus")
                .type(InstitutionType.POLYTECHNIC)
                .city("Kolkata")
                .state("West Bengal")
                .region("East")
                .status(InstitutionStatus.ACTIVE)
                .studentCount(4100)
                .servicesMonitored(6)
                .notes("Smaller institution with partial service coverage")
                .build());

        institutionRepository.save(Institution.builder()
                .institutionCode("COL-E-CENTRAL")
                .name("College E - Central Region")
                .type(InstitutionType.ARTS_AND_SCIENCE_COLLEGE)
                .city("Bhopal")
                .state("Madhya Pradesh")
                .region("Central")
                .status(InstitutionStatus.HIGH_RISK)
                .studentCount(5300)
                .servicesMonitored(7)
                .notes("Representative institution impacted by the high-risk Exam Portal scenario")
                .build());

        log.info("Seeded 6 representative NEDI institutions");
    }

    // ─────────────────────────────────────────────────────────────────
    // GRAPH — attack path edges (BFS traversal)
    // ─────────────────────────────────────────────────────────────────
    private void seedGraph() {
        if (relationshipRepository.count() > 0) {
            log.debug("Graph edges already seeded — skipping");
            return;
        }

        java.util.function.Consumer<AssetRelationship> save = relationshipRepository::save;

        // ── USER → FIREWALL (entry points) ─────────────────────────
        // viewer (student user) → DMZ firewall — LOW TRUST (attack entry)
        save.accept(AssetRelationship.builder()
                .sourceAssetId(3L).sourceType(AssetType.USER)
                .targetAssetId(1L).targetType(AssetType.FIREWALL)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(20).build());

        // serveradmin → Core firewall
        save.accept(AssetRelationship.builder()
                .sourceAssetId(2L).sourceType(AssetType.USER)
                .targetAssetId(2L).targetType(AssetType.FIREWALL)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(70).build());

        // admin → DMZ firewall (management)
        save.accept(AssetRelationship.builder()
                .sourceAssetId(1L).sourceType(AssetType.USER)
                .targetAssetId(1L).targetType(AssetType.FIREWALL)
                .relationshipType(RelationshipType.MANAGES)
                .trustLevel(90).build());

        // ── FIREWALL → SERVER (pivot points) ───────────────────────
        // NEDI-DMZ-FW-01 → NEDI-STUDENT-WEB-01 (LOW trust — internet-facing)
        save.accept(AssetRelationship.builder()
                .sourceAssetId(1L).sourceType(AssetType.FIREWALL)
                .targetAssetId(1L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(25).build());

        // NEDI-DMZ-FW-01 → NEDI-ERP-APP-01
        save.accept(AssetRelationship.builder()
                .sourceAssetId(1L).sourceType(AssetType.FIREWALL)
                .targetAssetId(5L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(30).build());

        // NEDI-CORE-FW-01 → NEDI-EXAM-APP-01
        save.accept(AssetRelationship.builder()
                .sourceAssetId(2L).sourceType(AssetType.FIREWALL)
                .targetAssetId(2L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(50).build());

        // NEDI-DB-FW-01 → NEDI-EXAM-DB-01
        save.accept(AssetRelationship.builder()
                .sourceAssetId(3L).sourceType(AssetType.FIREWALL)
                .targetAssetId(3L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(60).build());

        // ── SERVER → SERVER (lateral movement) ─────────────────────
        // NEDI-STUDENT-WEB-01 → NEDI-EXAM-APP-01 (lateral pivot)
        save.accept(AssetRelationship.builder()
                .sourceAssetId(1L).sourceType(AssetType.SERVER)
                .targetAssetId(2L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(40).build());

        // NEDI-EXAM-APP-01 → NEDI-EXAM-DB-01 (CRITICAL lateral move)
        save.accept(AssetRelationship.builder()
                .sourceAssetId(2L).sourceType(AssetType.SERVER)
                .targetAssetId(3L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(55).build());

        // NEDI-FACULTY-WEB-01 → NEDI-EXAM-DB-01 (backup job)
        save.accept(AssetRelationship.builder()
                .sourceAssetId(4L).sourceType(AssetType.SERVER)
                .targetAssetId(3L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(45).build());

        // ── SERVER → LICENSE (dependencies) ────────────────────────
        save.accept(AssetRelationship.builder()
                .sourceAssetId(3L).sourceType(AssetType.SERVER)
                .targetAssetId(1L).targetType(AssetType.LICENSE)
                .relationshipType(RelationshipType.HOSTS)
                .trustLevel(80).build());

        save.accept(AssetRelationship.builder()
                .sourceAssetId(1L).sourceType(AssetType.SERVER)
                .targetAssetId(2L).targetType(AssetType.LICENSE)
                .relationshipType(RelationshipType.HOSTS)
                .trustLevel(80).build());

        // ── HARDWARE → SERVER (infrastructure) ─────────────────────
        save.accept(AssetRelationship.builder()
                .sourceAssetId(1L).sourceType(AssetType.HARDWARE)
                .targetAssetId(1L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(85).build());

        save.accept(AssetRelationship.builder()
                .sourceAssetId(2L).sourceType(AssetType.HARDWARE)
                .targetAssetId(1L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(75).build());

        save.accept(AssetRelationship.builder()
                .sourceAssetId(2L).sourceType(AssetType.HARDWARE)
                .targetAssetId(2L).targetType(AssetType.SERVER)
                .relationshipType(RelationshipType.CONNECTS_TO)
                .trustLevel(75).build());

        log.info("Seeded 15 graph edges — BFS path: viewer → NEDI-DMZ-FW-01 → NEDI-STUDENT-WEB-01 → NEDI-EXAM-APP-01 → NEDI-EXAM-DB-01");
    }
}
