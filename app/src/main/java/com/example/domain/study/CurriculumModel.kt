package com.example.domain.study

/**
 * NCERT / CBSE Curriculum Hierarchy (Section 12)
 *
 * Designed to structure educational standards without dumping copyrighted textbook content.
 * Generates original conceptual explanations and practice questions mapped to standard curriculum topics.
 */
data class CurriculumTopic(
    val id: String,
    val name: String,
    val description: String = ""
)

data class CurriculumChapter(
    val id: String,
    val number: Int,
    val name: String,
    val topics: List<CurriculumTopic>
)

data class CurriculumSubject(
    val id: String,
    val name: String,
    val iconName: String,
    val chapters: List<CurriculumChapter>
)

data class CurriculumClass(
    val id: String,
    val name: String,
    val board: String = "CBSE",
    val academicYear: String = "2024-2025",
    val subjects: List<CurriculumSubject>
)

object CurriculumData {
    val CBSE_CLASS_10 = CurriculumClass(
        id = "class_10",
        name = "Class 10",
        board = "CBSE",
        academicYear = "2024-2025",
        subjects = listOf(
            CurriculumSubject(
                id = "math",
                name = "Mathematics",
                iconName = "Calculate",
                chapters = listOf(
                    CurriculumChapter(
                        id = "math_ch1",
                        number = 1,
                        name = "Real Numbers",
                        topics = listOf(
                            CurriculumTopic("t1", "Fundamental Theorem of Arithmetic"),
                            CurriculumTopic("t2", "Revisiting Irrational Numbers"),
                            CurriculumTopic("t3", "HCF and LCM Applications")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch2",
                        number = 2,
                        name = "Polynomials",
                        topics = listOf(
                            CurriculumTopic("t1", "Geometrical Meaning of Zeroes"),
                            CurriculumTopic("t2", "Relationship between Zeroes and Coefficients"),
                            CurriculumTopic("t3", "Quadratic Polynomial Formations")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch3",
                        number = 3,
                        name = "Pair of Linear Equations in Two Variables",
                        topics = listOf(
                            CurriculumTopic("t1", "Graphical Method of Solution"),
                            CurriculumTopic("t2", "Substitution Method"),
                            CurriculumTopic("t3", "Elimination Method"),
                            CurriculumTopic("t4", "Consistency of Linear Systems")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch4",
                        number = 4,
                        name = "Quadratic Equations",
                        topics = listOf(
                            CurriculumTopic("t1", "Standard Form of Quadratic Equations"),
                            CurriculumTopic("t2", "Solution by Factorisation"),
                            CurriculumTopic("t3", "Quadratic Formula"),
                            CurriculumTopic("t4", "Nature of Roots & Discriminant")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch5",
                        number = 5,
                        name = "Arithmetic Progressions",
                        topics = listOf(
                            CurriculumTopic("t1", "nth Term of an AP"),
                            CurriculumTopic("t2", "Sum of First n Terms of an AP"),
                            CurriculumTopic("t3", "Word Problems on Daily Life Situations")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch6",
                        number = 6,
                        name = "Triangles",
                        topics = listOf(
                            CurriculumTopic("t1", "Basic Proportionality Theorem (Thales)"),
                            CurriculumTopic("t2", "Criteria for Similarity of Triangles (AAA, SSS, SAS)")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch7",
                        number = 7,
                        name = "Coordinate Geometry",
                        topics = listOf(
                            CurriculumTopic("t1", "Distance Formula"),
                            CurriculumTopic("t2", "Section Formula"),
                            CurriculumTopic("t3", "Mid-point Formula")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch8",
                        number = 8,
                        name = "Introduction to Trigonometry",
                        topics = listOf(
                            CurriculumTopic("t1", "Trigonometric Ratios"),
                            CurriculumTopic("t2", "Values of Trigonometric Ratios of Specific Angles (0°, 30°, 45°, 60°, 90°)"),
                            CurriculumTopic("t3", "Trigonometric Identities (sin²θ + cos²θ = 1)")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch9",
                        number = 9,
                        name = "Some Applications of Trigonometry",
                        topics = listOf(
                            CurriculumTopic("t1", "Angle of Elevation and Depression"),
                            CurriculumTopic("t2", "Heights and Distances (Single & Double Triangle Problems)")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch10",
                        number = 10,
                        name = "Circles",
                        topics = listOf(
                            CurriculumTopic("t1", "Tangent to a Circle & Radius Perpendicularity"),
                            CurriculumTopic("t2", "Length of Tangents from an External Point")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch11",
                        number = 11,
                        name = "Areas Related to Circles",
                        topics = listOf(
                            CurriculumTopic("t1", "Area of Sector of a Circle"),
                            CurriculumTopic("t2", "Area of Segment of a Circle"),
                            CurriculumTopic("t3", "Combination of Plane Figures")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch12",
                        number = 12,
                        name = "Surface Areas and Volumes",
                        topics = listOf(
                            CurriculumTopic("t1", "Surface Area of Combinations of Solids"),
                            CurriculumTopic("t2", "Volume of Combinations of Solids")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch13",
                        number = 13,
                        name = "Statistics",
                        topics = listOf(
                            CurriculumTopic("t1", "Mean of Grouped Data (Direct & Assumed Mean)"),
                            CurriculumTopic("t2", "Mode of Grouped Data"),
                            CurriculumTopic("t3", "Median of Grouped Data")
                        )
                    ),
                    CurriculumChapter(
                        id = "math_ch14",
                        number = 14,
                        name = "Probability",
                        topics = listOf(
                            CurriculumTopic("t1", "Classical Definition of Probability"),
                            CurriculumTopic("t2", "Elementary & Complementary Events"),
                            CurriculumTopic("t3", "Coin, Dice, and Playing Card Problems")
                        )
                    )
                )
            ),
            CurriculumSubject(
                id = "science",
                name = "Science",
                iconName = "Science",
                chapters = listOf(
                    CurriculumChapter(
                        id = "sci_ch1",
                        number = 1,
                        name = "Chemical Reactions and Equations",
                        topics = listOf(
                            CurriculumTopic("t1", "Balancing Chemical Equations"),
                            CurriculumTopic("t2", "Combination, Decomposition, Displacement Reactions"),
                            CurriculumTopic("t3", "Oxidation, Reduction, Corrosion and Rancidity")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch2",
                        number = 2,
                        name = "Acids, Bases and Salts",
                        topics = listOf(
                            CurriculumTopic("t1", "Indicators & Chemical Properties"),
                            CurriculumTopic("t2", "pH Scale and Importance in Daily Life"),
                            CurriculumTopic("t3", "Baking Soda, Bleaching Powder, Plaster of Paris")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch3",
                        number = 3,
                        name = "Metals and Non-metals",
                        topics = listOf(
                            CurriculumTopic("t1", "Reactivity Series"),
                            CurriculumTopic("t2", "Ionic Bonds and Ionic Compounds"),
                            CurriculumTopic("t3", "Metallurgy & Corrosion Prevention")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch4",
                        number = 4,
                        name = "Carbon and its Compounds",
                        topics = listOf(
                            CurriculumTopic("t1", "Covalent Bonding in Carbon"),
                            CurriculumTopic("t2", "Homologous Series & Functional Groups"),
                            CurriculumTopic("t3", "Combustion, Oxidation, Addition & Substitution"),
                            CurriculumTopic("t4", "Soaps and Detergents (Micelles)")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch5",
                        number = 5,
                        name = "Life Processes",
                        topics = listOf(
                            CurriculumTopic("t1", "Autotrophic & Heterotrophic Nutrition"),
                            CurriculumTopic("t2", "Aerobic and Anaerobic Respiration"),
                            CurriculumTopic("t3", "Circulatory System & Human Heart"),
                            CurriculumTopic("t4", "Human Excretory System & Nephron")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch6",
                        number = 6,
                        name = "Control and Coordination",
                        topics = listOf(
                            CurriculumTopic("t1", "Neuron Structure & Reflex Arc"),
                            CurriculumTopic("t2", "Human Brain Anatomy"),
                            CurriculumTopic("t3", "Plant Hormones (Auxins, Gibberellins)"),
                            CurriculumTopic("t4", "Endocrine System & Hormones in Animals")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch7",
                        number = 7,
                        name = "How do Organisms Reproduce?",
                        topics = listOf(
                            CurriculumTopic("t1", "Asexual Reproduction (Binary Fission, Budding, Spores)"),
                            CurriculumTopic("t2", "Sexual Reproduction in Flowering Plants"),
                            CurriculumTopic("t3", "Human Reproductive System"),
                            CurriculumTopic("t4", "Reproductive Health & Contraception")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch8",
                        number = 8,
                        name = "Heredity and Evolution",
                        topics = listOf(
                            CurriculumTopic("t1", "Mendel's Laws of Inheritance"),
                            CurriculumTopic("t2", "Monohybrid & Dihybrid Crosses"),
                            CurriculumTopic("t3", "Sex Determination in Humans")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch9",
                        number = 9,
                        name = "Light – Reflection and Refraction",
                        topics = listOf(
                            CurriculumTopic("t1", "Spherical Mirrors & Mirror Formula"),
                            CurriculumTopic("t2", "Refraction & Snell's Law"),
                            CurriculumTopic("t3", "Spherical Lenses & Lens Formula"),
                            CurriculumTopic("t4", "Power of a Lens")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch10",
                        number = 10,
                        name = "The Human Eye and the Colourful World",
                        topics = listOf(
                            CurriculumTopic("t1", "Defects of Vision (Myopia, Hypermetropia, Presbyopia)"),
                            CurriculumTopic("t2", "Refraction of Light through a Prism"),
                            CurriculumTopic("t3", "Atmospheric Refraction & Twinkling of Stars"),
                            CurriculumTopic("t4", "Scattering of Light & Tyndall Effect")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch11",
                        number = 11,
                        name = "Electricity",
                        topics = listOf(
                            CurriculumTopic("t1", "Ohm's Law & Resistance"),
                            CurriculumTopic("t2", "Factors affecting Resistance & Resistivity"),
                            CurriculumTopic("t3", "Resistors in Series and Parallel"),
                            CurriculumTopic("t4", "Joule's Law of Heating & Electric Power")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch12",
                        number = 12,
                        name = "Magnetic Effects of Electric Current",
                        topics = listOf(
                            CurriculumTopic("t1", "Magnetic Field & Field Lines"),
                            CurriculumTopic("t2", "Right-Hand Thumb Rule & Solenoid"),
                            CurriculumTopic("t3", "Fleming's Left-Hand Rule"),
                            CurriculumTopic("t4", "Domestic Electric Circuits & Fuse/Earth Wire")
                        )
                    ),
                    CurriculumChapter(
                        id = "sci_ch13",
                        number = 13,
                        name = "Our Environment",
                        topics = listOf(
                            CurriculumTopic("t1", "Ecosystem Components & Food Chains"),
                            CurriculumTopic("t2", "10 Percent Energy Law & Biological Magnification"),
                            CurriculumTopic("t3", "Ozone Layer Depletion & Waste Management")
                        )
                    )
                )
            ),
            CurriculumSubject(
                id = "social_science",
                name = "Social Science",
                iconName = "Public",
                chapters = listOf(
                    CurriculumChapter(
                        id = "sst_hist_ch1",
                        number = 1,
                        name = "The Rise of Nationalism in Europe",
                        topics = listOf(
                            CurriculumTopic("t1", "French Revolution & Idea of Nation"),
                            CurriculumTopic("t2", "Making of Nationalism in Europe"),
                            CurriculumTopic("t3", "Unification of Germany and Italy")
                        )
                    ),
                    CurriculumChapter(
                        id = "sst_hist_ch2",
                        number = 2,
                        name = "Nationalism in India",
                        topics = listOf(
                            CurriculumTopic("t1", "First World War, Khilafat & Non-Cooperation"),
                            CurriculumTopic("t2", "Differing Strands within the Movement"),
                            CurriculumTopic("t3", "Towards Civil Disobedience & Salt March")
                        )
                    ),
                    CurriculumChapter(
                        id = "sst_geo_ch1",
                        number = 3,
                        name = "Resources and Development",
                        topics = listOf(
                            CurriculumTopic("t1", "Classification of Resources"),
                            CurriculumTopic("t2", "Resource Planning in India"),
                            CurriculumTopic("t3", "Land Degradation & Soil Types")
                        )
                    ),
                    CurriculumChapter(
                        id = "sst_civ_ch1",
                        number = 4,
                        name = "Power Sharing",
                        topics = listOf(
                            CurriculumTopic("t1", "Case Studies of Belgium and Sri Lanka"),
                            CurriculumTopic("t2", "Majoritarianism in Sri Lanka"),
                            CurriculumTopic("t3", "Forms of Power Sharing")
                        )
                    ),
                    CurriculumChapter(
                        id = "sst_econ_ch1",
                        number = 5,
                        name = "Development",
                        topics = listOf(
                            CurriculumTopic("t1", "What Development Promises"),
                            CurriculumTopic("t2", "National Development & Income Criteria"),
                            CurriculumTopic("t3", "Sustainability of Development")
                        )
                    ),
                    CurriculumChapter(
                        id = "sst_econ_ch2",
                        number = 6,
                        name = "Sectors of the Indian Economy",
                        topics = listOf(
                            CurriculumTopic("t1", "Primary, Secondary, and Tertiary Sectors"),
                            CurriculumTopic("t2", "Rising Importance of Tertiary Sector"),
                            CurriculumTopic("t3", "Organised vs Unorganised Sectors")
                        )
                    )
                )
            ),
            CurriculumSubject(
                id = "english",
                name = "English",
                iconName = "MenuBook",
                chapters = listOf(
                    CurriculumChapter(
                        id = "eng_ch1",
                        number = 1,
                        name = "First Flight: Prose & Poetry",
                        topics = listOf(
                            CurriculumTopic("t1", "A Letter to God (G.L. Fuentes)"),
                            CurriculumTopic("t2", "Nelson Mandela: Long Walk to Freedom"),
                            CurriculumTopic("t3", "Two Stories about Flying (His First Flight, Black Aeroplane)"),
                            CurriculumTopic("t4", "Dust of Snow & Fire and Ice (Robert Frost)")
                        )
                    ),
                    CurriculumChapter(
                        id = "eng_ch2",
                        number = 2,
                        name = "Footprints Without Feet",
                        topics = listOf(
                            CurriculumTopic("t1", "A Triumph of Surgery"),
                            CurriculumTopic("t2", "The Thief's Story"),
                            CurriculumTopic("t3", "Footprints Without Feet"),
                            CurriculumTopic("t4", "The Necklace & Bholi")
                        )
                    ),
                    CurriculumChapter(
                        id = "eng_ch3",
                        number = 3,
                        name = "Grammar & Creative Writing",
                        topics = listOf(
                            CurriculumTopic("t1", "Tenses & Modals"),
                            CurriculumTopic("t2", "Subject-Verb Concord & Reported Speech"),
                            CurriculumTopic("t3", "Analytical Paragraph Writing"),
                            CurriculumTopic("t4", "Formal Letter Writing (Editor, Inquiry, Complaint)")
                        )
                    )
                )
            ),
            CurriculumSubject(
                id = "hindi",
                name = "Hindi",
                iconName = "Translate",
                chapters = listOf(
                    CurriculumChapter(
                        id = "hin_ch1",
                        number = 1,
                        name = "क्षितिज भाग-2 (काव्य व गद्य खंड)",
                        topics = listOf(
                            CurriculumTopic("t1", "सूरदास के पद - भ्रमरगीत"),
                            CurriculumTopic("t2", "राम-लक्ष्मण-परशुराम संवाद"),
                            CurriculumTopic("t3", "नेताजी का चश्मा (स्वयं प्रकाश)"),
                            CurriculumTopic("t4", "बालगोबिन भगत (रामवृक्ष बेनीपुरी)")
                        )
                    ),
                    CurriculumChapter(
                        id = "hin_ch2",
                        number = 2,
                        name = "कृतिका भाग-2",
                        topics = listOf(
                            CurriculumTopic("t1", "माता का आँचल (शिवपूजन सहाय)"),
                            CurriculumTopic("t2", "जॉर्ज पंचम की नाक (कमलेश्वर)"),
                            CurriculumTopic("t3", "साना-साना हाथ जोड़ि (मधु कांकरिया)")
                        )
                    ),
                    CurriculumChapter(
                        id = "hin_ch3",
                        number = 3,
                        name = "व्यावहारिक व्याकरण",
                        topics = listOf(
                            CurriculumTopic("t1", "रचना के आधार पर वाक्य भेद"),
                            CurriculumTopic("t2", "वाच्य (कर्तृवाच्य, कर्मवाच्य, भाववाच्य)"),
                            CurriculumTopic("t3", "पद-परिचय"),
                            CurriculumTopic("t4", "रस और उसके अवयव")
                        )
                    )
                )
            )
        )
    )

    val AVAILABLE_CLASSES = listOf(
        CBSE_CLASS_10
    )

    fun getSubjectsForClass(classId: String = "class_10"): List<CurriculumSubject> {
        return AVAILABLE_CLASSES.firstOrNull { it.id == classId }?.subjects ?: CBSE_CLASS_10.subjects
    }
}
