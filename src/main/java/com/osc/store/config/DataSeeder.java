package com.osc.store.config;

import com.osc.store.model.*;
import com.osc.store.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates the super admin on first start and, on an empty database, a small sample catalogue
 * so the storefront is not blank. Sample data can be deleted from the admin panel.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AdminUserRepository admins;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final OptionDefinitionRepository options;
    private final ProductRepository products;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DataSeeder(AdminUserRepository admins, CategoryRepository categories, BrandRepository brands,
                      OptionDefinitionRepository options, ProductRepository products,
                      PasswordEncoder encoder, AppProperties props) {
        this.admins = admins;
        this.categories = categories;
        this.brands = brands;
        this.options = options;
        this.products = products;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSuperAdmin();
        if (categories.count() == 0 && brands.count() == 0 && products.count() == 0) {
            seedCatalogue();
        }
    }

    private void seedSuperAdmin() {
        AppProperties.SuperAdmin cfg = props.getSuperAdmin();
        AdminUser user = admins.findByEmailIgnoreCase(cfg.getEmail()).orElse(null);
        if (user == null) {
            user = new AdminUser();
            user.setEmail(cfg.getEmail().toLowerCase());
            user.setName(cfg.getName());
            user.setPasswordHash(encoder.encode(cfg.getPassword()));
            user.setRole(AdminRole.SUPER_ADMIN);
            admins.save(user);
            log.info("Created super admin {}", cfg.getEmail());
        } else if (!user.isSuperAdmin()) {
            user.setRole(AdminRole.SUPER_ADMIN);
            admins.save(user);
        }
    }

    private void seedCatalogue() {
        Category networking = category("Networking", "Switches, routers, wireless and structured cabling");
        Category computing = category("Computing", "Servers, workstations, laptops and storage");
        Category security = category("Security & Surveillance", "CCTV, access control and alarm systems");
        Category power = category("Power & Backup", "UPS systems, inverters and batteries");

        Brand cisco = brand("Cisco");
        Brand ubiquiti = brand("Ubiquiti");
        Brand dell = brand("Dell");
        Brand hikvision = brand("Hikvision");
        Brand apc = brand("APC");

        OptionDefinition memory = option("Memory", "8GB", "16GB", "32GB", "64GB");
        OptionDefinition ports = option("Port count", "24 ports", "48 ports");
        OptionDefinition warranty = option("Warranty term", "1 year", "3 years", "5 years");
        OptionDefinition resolution = option("Resolution", "4MP", "8MP");

        product(ProductType.PRODUCT, "Catalyst 9200 Managed Switch", "SW-C9200", networking, cisco,
                "Enterprise-grade managed access switch with PoE+ and stacking support.",
                "A flexible, secure access switch for campus and branch networks. Includes full Layer 3 features, "
                        + "PoE+ on every port and built-in security controls.",
                List.of(new ProductSpec("Switching capacity", "176 Gbps"), new ProductSpec("PoE budget", "740 W"),
                        new ProductSpec("Uplinks", "4 x 1G SFP"), new ProductSpec("Form factor", "1U rack mount")),
                List.of(ports, warranty), true);
        product(ProductType.PRODUCT, "UniFi Dream Machine Pro", "UBQ-UDMP", networking, ubiquiti,
                "All-in-one gateway, controller and 8-port switch for small to mid-size sites.",
                "Combines routing, switching, network application hosting and security gateway features in a single rack unit.",
                List.of(new ProductSpec("Throughput", "3.5 Gbps IPS"), new ProductSpec("Ports", "8 x GbE, 2 x SFP+"),
                        new ProductSpec("Form factor", "1U rack mount")),
                List.of(warranty), true);
        product(ProductType.PRODUCT, "PowerEdge R450 Rack Server", "DL-R450", computing, dell,
                "Dual-socket 1U rack server built for virtualisation and business workloads.",
                "Intel Xeon Scalable processors, up to 8 x 2.5\" drive bays and iDRAC remote management.",
                List.of(new ProductSpec("Processor", "Intel Xeon Silver"), new ProductSpec("Drive bays", "8 x 2.5\""),
                        new ProductSpec("Management", "iDRAC9 Enterprise")),
                List.of(memory, warranty), true);
        product(ProductType.PRODUCT, "ColorVu 4MP Bullet Camera", "HK-CV4B", security, hikvision,
                "Full-colour night vision IP camera with weatherproof housing.",
                "24/7 colour imaging with a fast aperture lens and built-in white light for clear footage in the dark.",
                List.of(new ProductSpec("Sensor", "1/1.8\" progressive scan CMOS"), new ProductSpec("Protection", "IP67"),
                        new ProductSpec("Night range", "Up to 40 m")),
                List.of(resolution, warranty), false);
        product(ProductType.PRODUCT, "Smart-UPS 3000VA Rack/Tower", "APC-SMT3000", power, apc,
                "Line-interactive UPS with network management card and hot-swappable batteries.",
                "Dependable power protection for servers, storage and network equipment.",
                List.of(new ProductSpec("Capacity", "3000 VA / 2700 W"), new ProductSpec("Runtime", "Up to 18 min at half load"),
                        new ProductSpec("Outlets", "8 x C13, 1 x C19")),
                List.of(warranty), false);

        product(ProductType.SOLUTION, "Office Network Rollout", "SOL-NET", networking, null,
                "Design, supply and installation of a complete office network.",
                "Site survey, structured cabling, switching, Wi-Fi coverage and documentation, delivered by certified engineers.",
                List.of(new ProductSpec("Includes", "Survey, design, supply, installation, commissioning"),
                        new ProductSpec("Support", "30 days post-install support")),
                List.of(), true);
        product(ProductType.SOLUTION, "Managed CCTV Solution", "SOL-CCTV", security, null,
                "End-to-end surveillance with cameras, recording and remote monitoring.",
                "We assess your premises, supply the cameras and recorders, install and configure remote viewing.",
                List.of(new ProductSpec("Includes", "Cameras, NVR, cabling, installation, mobile app setup")),
                List.of(), true);
        product(ProductType.SOLUTION, "Backup Power Package", "SOL-PWR", power, null,
                "Sized UPS and battery backup to keep critical systems running during outages.",
                "Load assessment, equipment supply, installation and testing for server rooms and small offices.",
                List.of(new ProductSpec("Includes", "Load assessment, UPS, batteries, installation, testing")),
                List.of(), false);
        log.info("Seeded sample catalogue");
    }

    private Category category(String name, String description) {
        Category c = new Category();
        c.setName(name);
        c.setDescription(description);
        return categories.save(c);
    }

    private Brand brand(String name) {
        Brand b = new Brand();
        b.setName(name);
        return brands.save(b);
    }

    private OptionDefinition option(String name, String... values) {
        OptionDefinition o = new OptionDefinition();
        o.setName(name);
        o.setOptionValues(new ArrayList<>(List.of(values)));
        return options.save(o);
    }

    private void product(ProductType type, String name, String sku, Category category, Brand brand, String summary,
                         String description, List<ProductSpec> specs, List<OptionDefinition> opts, boolean featured) {
        Product p = new Product();
        p.setType(type);
        p.setName(name);
        p.setSku(sku);
        p.setCategory(category);
        p.setBrand(brand);
        p.setSummary(summary);
        p.setDescription(description);
        p.getSpecifications().addAll(specs);
        p.getOptions().addAll(opts);
        p.setFeatured(featured);
        products.save(p);
    }
}
