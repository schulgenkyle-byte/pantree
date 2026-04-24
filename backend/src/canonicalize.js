// Canonical ingredient names. Adding a synonym here + redeploy + running the
// re-canonicalize migration propagates to all existing rows.
//
// Rule: the KEY is the canonical display form. Values are known aliases (all lowercase).
// Aliases include US/UK/AU/NZ spellings, brand-names, common user typos, regional variants.
//
// IMPORTANT: `cilantro` (herb, leaves) is DISTINCT from `coriander seed` (spice, seeds).
// Users writing "coriander" by itself are usually referring to the herb (cilantro) in
// US English, so bare "coriander" canonicalizes to cilantro. Recipes that mean the spice
// should say "coriander seed" or "ground coriander" — both routed to `coriander seed`.

const SYNONYMS = {
  // --- produce: alliums ---
  'scallion': ['green onion', 'green onions', 'spring onion', 'spring onions', 'scallions', 'salad onion', 'salad onions', 'welsh onion'],
  'shallot': ['shallots', 'french shallot', 'echalote', 'eschalot', 'eschalots'],
  'garlic': ['garlic cloves', 'garlic clove', 'fresh garlic', 'minced garlic', 'crushed garlic'],
  'onion': ['yellow onion', 'white onion', 'brown onion', 'red onion', 'spanish onion', 'sweet onion', 'vidalia onion'],
  'leek': ['leeks'],

  // --- produce: herbs ---
  // cilantro == the leaf (herb). "coriander" bare maps here per common US/UK recipe usage of the fresh herb.
  'cilantro': ['coriander', 'coriander leaves', 'coriander leaf', 'fresh coriander', 'chinese parsley', 'dhania', 'kothimbir'],
  'parsley': ['flat leaf parsley', 'flat-leaf parsley', 'italian parsley', 'curly parsley', 'fresh parsley'],
  'basil': ['sweet basil', 'italian basil', 'fresh basil'],
  'thai basil': ['holy basil', 'horapha'],
  'mint': ['spearmint', 'fresh mint', 'peppermint leaves'],
  'oregano': ['dried oregano', 'fresh oregano', 'mexican oregano', 'greek oregano'],
  'thyme': ['fresh thyme', 'dried thyme', 'lemon thyme'],
  'rosemary': ['dried rosemary', 'fresh rosemary'],
  'dill': ['dill weed', 'fresh dill'],
  'chives': ['chive', 'fresh chives'],
  'tarragon': ['fresh tarragon', 'french tarragon'],
  'sage': ['fresh sage', 'dried sage'],

  // --- produce: greens ---
  'arugula': ['rocket', 'roquette', 'rucola', 'rugula', 'rocket leaves', 'salad rocket'],
  'spinach': ['baby spinach', 'fresh spinach', 'english spinach'],
  'romaine': ['romaine lettuce', 'cos lettuce', 'cos'],
  'bok choy': ['pak choi', 'pak choy', 'bok choi', 'pak-choi'],
  'napa cabbage': ['chinese cabbage', 'wong bok', 'wombok', 'chinese leaves'],
  'endive': ['chicory', 'belgian endive', 'witloof'],
  'kale': ['lacinato kale', 'dinosaur kale', 'tuscan kale', 'cavolo nero'],
  'swiss chard': ['rainbow chard', 'chard', 'silverbeet'],
  'watercress': ['water cress'],
  'radicchio': ['italian chicory'],

  // --- produce: vegetables ---
  'eggplant': ['aubergine', 'aubergines', 'brinjal', 'brinjals', 'baingan', 'melongene', 'garden egg'],
  'zucchini': ['courgette', 'courgettes', 'baby marrow', 'baby marrows', 'summer squash'],
  'bell pepper': ['capsicum', 'capsicums', 'sweet pepper', 'sweet peppers', 'red bell pepper', 'green bell pepper', 'yellow bell pepper', 'orange bell pepper', 'bell peppers', 'red capsicum', 'green capsicum', 'yellow capsicum'],
  'jalapeno': ['jalapeño', 'jalapeños', 'jalapeno pepper', 'jalapeno peppers', 'jalapeño pepper', 'jalapeño peppers', 'jalepeno', 'jalepeño'],
  'serrano': ['serrano pepper', 'serrano chile', 'serrano chili', 'serrano chilli', 'serrano peppers'],
  'habanero': ['habanero pepper', 'scotch bonnet'],
  'chili pepper': ['chilli pepper', 'chilli', 'chili', 'hot pepper', 'red chili', 'green chili', 'red chilli', 'green chilli', 'bird\'s eye chili', 'birds eye chili', 'thai chili', 'thai chilli'],
  'poblano': ['poblano pepper', 'ancho pepper'],
  'pepperoncini': ['peperoncino', 'peperoncini', 'italian pickled pepper', 'italian pickled peppers', 'golden greek peppers', 'tuscan peppers'],
  'tomato': ['tomatoes', 'roma tomato', 'roma tomatoes', 'plum tomato', 'plum tomatoes', 'cherry tomatoes', 'cherry tomato', 'grape tomato', 'grape tomatoes', 'vine tomato', 'vine-ripened tomato', 'beefsteak tomato', 'beefsteak tomatoes'],
  'cucumber': ['cucumbers', 'english cucumber', 'persian cucumber', 'lebanese cucumber'],
  'mushroom': ['mushrooms', 'button mushrooms', 'cremini mushrooms', 'white mushrooms', 'chestnut mushrooms', 'swiss brown mushrooms'],
  'portobello': ['portobello mushroom', 'portobello mushrooms', 'portabella', 'portabello'],
  'shiitake': ['shiitake mushroom', 'shiitake mushrooms'],
  'oyster mushroom': ['oyster mushrooms'],
  'celery': ['celery stalks', 'celery stalk'],
  'carrot': ['carrots', 'baby carrots'],
  'potato': ['potatoes', 'russet potato', 'russet potatoes', 'yukon gold potato', 'yukon gold', 'new potato', 'new potatoes', 'fingerling potatoes', 'maris piper', 'king edward'],
  'sweet potato': ['sweet potatoes', 'yam', 'yams', 'kumara', 'kumaras'],
  'broccoli': ['broccoli florets', 'broccoli crowns', 'calabrese'],
  'cauliflower': ['cauliflower florets'],
  'cabbage': ['green cabbage', 'savoy cabbage', 'red cabbage'],
  'brussels sprouts': ['brussel sprouts', 'brussels sprout', 'brussel sprout'],
  'asparagus': ['asparagus spears'],
  'corn': ['sweet corn', 'corn kernels', 'fresh corn', 'corn on the cob', 'maize', 'mealies'],
  'peas': ['green peas', 'garden peas', 'english peas', 'frozen peas'],
  'snow pea': ['snow peas', 'mangetout', 'mange tout', 'mange-tout', 'chinese pea pods'],
  'snap peas': ['sugar snap peas', 'sugar snaps', 'snap pea', 'sugar snap pea'],
  'green beans': ['string beans', 'haricot verts', 'haricots verts', 'french beans', 'runner beans'],
  'edamame': ['edamame beans', 'soybeans', 'soya beans'],
  'beet': ['beetroot', 'beets', 'beetroots', 'red beet', 'garden beet'],
  'turnip': ['turnips', 'white turnip'],
  'rutabaga': ['swede', 'swedes', 'swedish turnip', 'yellow turnip', 'neep', 'neeps'],
  'parsnip': ['parsnips'],
  'radish': ['radishes'],
  'daikon': ['daikon radish', 'mooli', 'white radish'],
  'ginger': ['fresh ginger', 'ginger root', 'root ginger'],
  'lemongrass': ['lemon grass'],
  'galangal': ['thai ginger', 'blue ginger'],
  'avocado': ['avocados', 'hass avocado'],
  'okra': ['ladies finger', 'ladies fingers', 'lady\'s finger', 'bhindi', 'gumbo'],
  'fennel': ['fennel bulb', 'florence fennel', 'finocchio'],

  // --- fruit ---
  'lemon': ['lemons', 'lemon juice', 'fresh lemon juice'],
  'lime': ['limes', 'lime juice', 'fresh lime juice'],
  'orange': ['oranges', 'navel orange', 'valencia orange'],
  'apple': ['apples', 'granny smith', 'honeycrisp', 'fuji apple', 'red apple', 'green apple'],
  'banana': ['bananas', 'ripe banana'],
  'strawberry': ['strawberries', 'fresh strawberries'],
  'blueberry': ['blueberries', 'fresh blueberries'],
  'raspberry': ['raspberries'],
  'blackberry': ['blackberries'],
  'mango': ['mangos', 'mangoes', 'ripe mango'],
  'pineapple': ['fresh pineapple', 'pineapple chunks'],
  'peach': ['peaches', 'nectarine', 'nectarines'],
  'pear': ['pears', 'bartlett pear'],
  'grape': ['grapes', 'red grapes', 'green grapes'],
  'cherry': ['cherries', 'fresh cherries', 'pitted cherries'],
  'pomegranate': ['pomegranate seeds', 'pomegranate arils'],
  'coconut': ['shredded coconut', 'coconut flakes', 'desiccated coconut'],
  'raisins': ['raisin', 'currants', 'dried grapes'],
  'golden raisins': ['sultana', 'sultanas', 'golden sultanas'],

  // --- protein: poultry ---
  'chicken breast': ['chicken breasts', 'boneless skinless chicken breast', 'boneless chicken breast', 'chicken breast fillets'],
  'chicken thigh': ['chicken thighs', 'boneless chicken thighs', 'boneless skinless chicken thighs', 'bone-in chicken thighs'],
  'whole chicken': ['roasting chicken', 'fryer chicken'],
  'chicken wings': ['chicken wing', 'wing'],
  'ground chicken': ['chicken mince', 'minced chicken'],
  'rotisserie chicken': ['roast chicken', 'cooked chicken', 'shredded chicken', 'pulled chicken'],
  'turkey breast': ['turkey breasts'],
  'ground turkey': ['turkey mince', 'minced turkey'],

  // --- protein: beef/pork/lamb ---
  // Generic mince/ground meat — users should specify beef/pork; we still dedupe the term.
  'ground meat': ['minced meat', 'mince meat', 'mince', 'minced', 'ground meat (unspecified)'],
  'ground beef': ['beef mince', 'minced beef', 'hamburger meat', 'hamburger', 'hamburger mince', 'lean ground beef', 'extra lean ground beef'],
  'beef chuck': ['chuck roast', 'chuck steak', 'stewing beef', 'stew meat', 'braising steak'],
  'steak': ['beef steak', 'sirloin steak', 'ribeye', 'rib-eye', 'ribeye steak', 'strip steak', 'new york strip'],
  'beef tenderloin': ['filet mignon', 'beef fillet', 'tenderloin', 'eye fillet'],
  'short ribs': ['beef short ribs'],
  'brisket': ['beef brisket'],
  'pork chop': ['pork chops', 'pork loin chop'],
  'pork loin': ['pork tenderloin'],
  'pork shoulder': ['pork butt', 'boston butt', 'pork collar'],
  'ground pork': ['pork mince', 'minced pork'],
  'bacon': ['streaky bacon', 'back bacon', 'bacon strips', 'bacon rashers', 'rashers'],
  'ham': ['gammon', 'deli ham', 'sliced ham'],
  'prosciutto': ['parma ham'],
  'sausage': ['italian sausage', 'breakfast sausage', 'pork sausage', 'sausage links', 'bangers'],
  'chorizo': ['spanish chorizo', 'mexican chorizo'],
  'lamb chops': ['lamb chop', 'lamb loin chops'],
  'ground lamb': ['lamb mince', 'minced lamb'],

  // --- protein: seafood ---
  'shrimp': ['prawn', 'prawns', 'king prawns', 'tiger prawns', 'jumbo shrimp', 'shrimps'],
  'salmon': ['salmon fillet', 'salmon filet', 'salmon fillets', 'atlantic salmon', 'sockeye salmon'],
  'tuna': ['tuna steak', 'ahi tuna', 'albacore', 'tuna fillet'],
  'canned tuna': ['tuna in water', 'tuna in oil'],
  'cod': ['cod fillet', 'cod fillets'],
  'tilapia': ['tilapia fillet', 'tilapia fillets'],
  'sea bass': ['branzino', 'european bass'],
  'halibut': ['halibut fillet'],
  'scallops': ['sea scallops', 'bay scallops'],
  'mussels': ['mussel'],
  'clams': ['clam'],
  'anchovies': ['anchovy', 'anchovy fillets'],
  'sardines': ['sardine', 'canned sardines', 'pilchards'],

  // --- protein: plant ---
  'tofu': ['firm tofu', 'extra firm tofu', 'silken tofu', 'soft tofu', 'bean curd'],
  'tempeh': ['tempe'],
  'seitan': ['wheat gluten', 'vital wheat gluten'],

  // --- dairy ---
  'milk': ['whole milk', 'whole dairy milk', '2% milk', '1% milk', 'skim milk', 'dairy milk', 'semi-skimmed milk', 'semi skimmed milk', 'full fat milk', 'full-fat milk'],
  'buttermilk': ['cultured buttermilk'],
  // Heavy cream cluster — US "heavy cream" == UK "double cream" == AU "thickened cream".
  'heavy cream': ['heavy whipping cream', 'double cream', 'whipping cream', 'thickened cream', 'pure cream'],
  // Light cream cluster — keep separate from half-and-half intentionally, but US labels often overlap.
  'light cream': ['single cream', 'table cream', 'coffee cream', 'pouring cream'],
  'half and half': ['half-and-half'],
  // Sour cream deliberately kept SEPARATE from crème fraîche — similar but different fat/culture (see README).
  'sour cream': ['soured cream', 'crema', 'crema mexicana'],
  'crème fraîche': ['creme fraiche', 'crème fraiche', 'creme fraîche'],
  'yogurt': ['plain yogurt', 'greek yogurt', 'plain greek yogurt', 'yoghurt', 'natural yoghurt', 'natural yogurt'],
  'cream cheese': ['soft cheese', 'philadelphia'],
  'cottage cheese': ['curd cheese'],
  'ricotta': ['ricotta cheese'],
  'mozzarella': ['mozzarella cheese', 'fresh mozzarella', 'low-moisture mozzarella', 'shredded mozzarella', 'mozarella', 'mozarella cheese', 'fior di latte', 'bocconcini'],
  'cheddar': ['cheddar cheese', 'sharp cheddar', 'mild cheddar', 'extra sharp cheddar', 'mature cheddar', 'tasty cheese'],
  'parmesan': ['parmigiano', 'parmigiano-reggiano', 'parmigiano reggiano', 'parmesan cheese', 'grated parmesan', 'parmesean', 'parmasan', 'parmigiano cheese'],
  'pecorino': ['pecorino romano', 'romano cheese'],
  'feta': ['feta cheese', 'greek feta'],
  'goat cheese': ['chevre', 'chèvre', 'goat\'s cheese', 'goats cheese'],
  'gruyere': ['gruyère'],
  'blue cheese': ['gorgonzola', 'roquefort', 'stilton', 'bleu cheese'],
  'brie': ['brie cheese'],
  'swiss cheese': ['emmental', 'emmenthal', 'emmentaler'],
  'butter': ['unsalted butter', 'salted butter', 'sweet cream butter'],
  'eggs': ['egg', 'large eggs', 'whole eggs', 'fresh eggs'],
  'egg whites': ['egg white'],
  'egg yolks': ['egg yolk'],

  // --- dairy alternatives ---
  'almond milk': ['unsweetened almond milk'],
  'oat milk': ['oatmilk'],
  'soy milk': ['soymilk', 'soya milk'],
  'coconut milk': ['full fat coconut milk', 'coconut milk (canned)', 'canned coconut milk', 'full-fat coconut milk'],

  // --- grains / starches ---
  'all-purpose flour': ['plain flour', 'ap flour', 'white flour', 'wheat flour', 'all purpose flour'],
  'bread flour': ['strong flour', 'high-gluten flour', 'strong white flour', 'strong bread flour'],
  'whole wheat flour': ['whole-wheat flour', 'wholewheat flour', 'wholemeal flour', 'atta'],
  'self-rising flour': ['self-raising flour', 'self rising flour', 'self raising flour', 'self-rising wheat flour'],
  'almond flour': ['almond meal', 'ground almonds'],
  'cornmeal': ['corn meal', 'polenta', 'masa harina'],
  // NB: UK "cornflour" == US "cornstarch" (the thickener). US "corn flour" means ground corn (closer to cornmeal).
  // Pragmatically, recipe corpora almost always use the UK term to mean the thickener, so we route cornflour here.
  'cornstarch': ['corn starch', 'cornflour', 'corn flour'],
  'breadcrumbs': ['bread crumbs', 'panko', 'panko breadcrumbs'],
  'rice': ['white rice', 'long grain rice', 'basmati rice', 'jasmine rice'],
  'brown rice': ['whole grain rice'],
  'arborio rice': ['risotto rice', 'short grain rice', 'carnaroli'],
  'pasta': ['spaghetti', 'penne', 'fusilli', 'rigatoni', 'linguine', 'fettuccine', 'macaroni', 'rotini'],
  'lasagna noodles': ['lasagne', 'lasagne sheets', 'lasagna sheets'],
  'rice noodles': ['rice vermicelli', 'rice stick noodles', 'bun'],
  'egg noodles': ['wide egg noodles'],
  'ramen noodles': ['ramen'],
  'soba noodles': ['soba', 'buckwheat noodles'],
  'udon noodles': ['udon'],
  'couscous': ['israeli couscous', 'pearl couscous'],
  'quinoa': ['white quinoa', 'red quinoa', 'tricolor quinoa'],
  'barley': ['pearl barley', 'pearled barley'],
  'farro': ['emmer wheat'],
  'oats': ['rolled oats', 'old fashioned oats', 'quick oats', 'oatmeal', 'porridge oats'],
  'steel cut oats': ['steel-cut oats', 'irish oats', 'pinhead oats'],
  'bread': ['white bread', 'wholemeal bread', 'wholewheat bread', 'sourdough bread', 'sandwich bread'],
  'tortilla': ['tortillas', 'flour tortilla', 'flour tortillas', 'corn tortilla', 'corn tortillas'],
  'pita': ['pita bread', 'pitas', 'pitta', 'pitta bread'],
  'bagel': ['bagels'],

  // --- legumes ---
  'chickpea': ['chickpeas', 'garbanzo beans', 'garbanzo bean', 'garbanzos', 'garbanzo', 'canned chickpeas', 'chick pea', 'chick peas', 'ceci beans', 'ceci'],
  'black beans': ['black bean', 'canned black beans', 'turtle beans', 'black turtle beans'],
  'kidney beans': ['red kidney beans', 'canned kidney beans', 'rajma'],
  'pinto beans': ['canned pinto beans'],
  'cannellini beans': ['white beans', 'great northern beans', 'navy beans', 'haricot beans'],
  'lentils': ['lentil', 'green lentils', 'red lentils', 'yellow lentils', 'puy lentils', 'brown lentils', 'masoor dal', 'toor dal'],
  'split peas': ['yellow split peas', 'green split peas', 'chana dal'],
  'black-eyed peas': ['black eyed peas', 'black-eyed beans', 'cowpeas', 'lobia'],

  // --- nuts/seeds ---
  'almonds': ['almond', 'whole almonds', 'slivered almonds', 'sliced almonds'],
  'walnuts': ['walnut', 'walnut halves'],
  'pecans': ['pecan', 'pecan halves'],
  'cashews': ['cashew', 'raw cashews'],
  'peanuts': ['peanut', 'roasted peanuts', 'groundnuts', 'goober peas'],
  'pistachios': ['pistachio'],
  'pine nuts': ['pignoli', 'pinon', 'pinenuts', 'pinoli'],
  'sesame seeds': ['toasted sesame seeds', 'black sesame seeds', 'white sesame', 'til'],
  'chia seeds': ['chia seed'],
  'flax seeds': ['flaxseed', 'linseed', 'linseeds'],
  'sunflower seeds': ['sunflower seed'],
  'peanut butter': ['pb'],
  'almond butter': ['almond spread'],
  'tahini': ['sesame paste', 'tahina'],

  // --- condiments / sauces ---
  'soy sauce': ['shoyu', 'light soy sauce', 'dark soy sauce', 'tamari'],
  'fish sauce': ['nam pla', 'nuoc mam', 'patis'],
  'oyster sauce': ['oyster flavor sauce', 'oyster-flavored sauce'],
  'hoisin sauce': ['hoisin'],
  'worcestershire sauce': ['worcestershire', 'worcester sauce', 'worcestershire-sauce', 'l&p sauce'],
  'ketchup': ['tomato ketchup', 'catsup', 'tomato sauce (condiment)'],
  'mustard': ['yellow mustard', 'dijon mustard', 'whole grain mustard', 'english mustard'],
  'mayonnaise': ['mayo', 'kewpie'],
  'sriracha': ['sriracha sauce', 'rooster sauce'],
  'hot sauce': ['tabasco', 'louisiana hot sauce', 'frank\'s red hot', 'chili sauce'],
  'bbq sauce': ['barbecue sauce'],
  'tomato sauce': ['passata', 'crushed tomatoes', 'canned crushed tomatoes'],
  'tomato paste': ['tomato puree', 'tomato purée', 'tomato concentrate', 'concentrated tomato', 'double concentrate tomato'],
  'salsa': ['salsa roja'],
  'pesto': ['basil pesto', 'pesto sauce'],

  // --- vinegars ---
  'apple cider vinegar': ['cider vinegar', 'acv'],
  'balsamic vinegar': ['balsamic', 'aged balsamic'],
  'red wine vinegar': ['red wine vin'],
  'white wine vinegar': ['white wine vin'],
  'rice vinegar': ['rice wine vinegar', 'seasoned rice vinegar'],
  'white vinegar': ['distilled vinegar', 'distilled white vinegar'],
  'malt vinegar': ['brown vinegar'],

  // --- oils ---
  'olive oil': ['extra virgin olive oil', 'evoo', 'virgin olive oil'],
  'vegetable oil': ['neutral oil', 'canola oil', 'rapeseed oil'],
  'sesame oil': ['toasted sesame oil'],
  'coconut oil': ['virgin coconut oil'],
  'peanut oil': ['groundnut oil'],

  // --- spices (whole vs ground usually treated together) ---
  'salt': ['kosher salt', 'sea salt', 'table salt', 'fine salt', 'flaky salt', 'maldon', 'rock salt', 'himalayan salt', 'pink salt'],
  'black pepper': ['ground black pepper', 'cracked black pepper', 'peppercorns', 'black peppercorns'],
  'white pepper': ['white peppercorns'],
  'paprika': ['sweet paprika', 'smoked paprika', 'pimenton', 'pimentón', 'hungarian paprika'],
  'cumin': ['ground cumin', 'cumin seed', 'cumin seeds', 'jeera'],
  // The SPICE — intentionally distinct from the herb `cilantro` above.
  'coriander seed': ['ground coriander', 'coriander seeds', 'coriander powder', 'dhania powder'],
  'cinnamon': ['ground cinnamon', 'cinnamon stick', 'cinnamon sticks', 'cassia'],
  'nutmeg': ['ground nutmeg', 'whole nutmeg'],
  'cloves': ['ground cloves', 'whole cloves'],
  'cardamom': ['ground cardamom', 'cardamom pods', 'green cardamom', 'elaichi'],
  'turmeric': ['ground turmeric', 'fresh turmeric', 'haldi'],
  'ginger powder': ['ground ginger', 'dry ginger'],
  'chili powder': ['chilli powder', 'ground chili', 'ground chilli'],
  'cayenne': ['cayenne pepper', 'red pepper'],
  'red pepper flakes': ['crushed red pepper', 'chili flakes', 'chilli flakes', 'red chili flakes', 'red chilli flakes'],
  'bay leaves': ['bay leaf', 'laurel leaves'],
  'allspice': ['jamaican pepper', 'pimento'],
  'fennel seeds': ['fennel seed'],
  'mustard seeds': ['mustard seed'],
  'star anise': ['star aniseed', 'badiane'],
  'saffron': ['saffron threads', 'kesar'],
  'garam masala': [],
  'five spice': ['chinese five spice', '5-spice', 'five-spice powder', 'chinese 5 spice'],
  'herbes de provence': [],
  'italian seasoning': [],
  'za\'atar': ['zaatar', 'zatar'],
  'sumac': [],
  'cream of tartar': ['potassium bitartrate', 'tartaric acid salt'],

  // --- baking ---
  'baking soda': ['sodium bicarbonate', 'bicarbonate of soda', 'bicarb', 'bicarb soda'],
  'baking powder': ['double-acting baking powder'],
  'yeast': ['active dry yeast', 'instant yeast', 'rapid rise yeast', 'fresh yeast'],
  'vanilla': ['vanilla extract', 'pure vanilla extract', 'vanilla essence', 'vanilla bean paste'],
  'sugar': ['granulated sugar', 'white sugar', 'white granulated sugar'],
  'superfine sugar': ['caster sugar', 'castor sugar', 'baker\'s sugar', 'bakers sugar', 'berry sugar'],
  'brown sugar': ['light brown sugar', 'dark brown sugar', 'muscovado', 'demerara', 'demerara sugar', 'turbinado', 'turbinado sugar'],
  'powdered sugar': ['confectioners sugar', 'icing sugar', 'confectioner\'s sugar', 'confectioners\' sugar', '10x sugar'],
  'honey': ['raw honey', 'clover honey', 'runny honey'],
  'maple syrup': ['pure maple syrup'],
  'molasses': ['treacle', 'black treacle', 'blackstrap molasses'],
  'golden syrup': ['light treacle', 'golden sugar syrup', 'lyle\'s golden syrup'],
  'chocolate chips': ['chocolate morsels', 'chocolate chunks', 'choc chips'],
  'cocoa powder': ['unsweetened cocoa powder', 'dutch cocoa', 'dutch-processed cocoa', 'cacao powder'],
  'dark chocolate': ['bittersweet chocolate', 'semisweet chocolate', 'semi-sweet chocolate', 'plain chocolate'],
  'white chocolate': [],

  // --- pantry staples / canned ---
  'chicken broth': ['chicken stock', 'chicken bouillon'],
  'beef broth': ['beef stock'],
  'vegetable broth': ['vegetable stock', 'veg stock'],
  'coconut cream': ['creamed coconut'],
  'diced tomatoes': ['canned diced tomatoes', 'diced tomato', 'chopped tomatoes', 'canned chopped tomatoes'],
  'whole tomatoes': ['canned whole tomatoes', 'whole peeled tomatoes', 'san marzano tomatoes'],
  'tomato puree': ['tomato pasata', 'passata'],
  'pickles': ['dill pickles', 'gherkins', 'cornichons'],
  'capers': ['caper'],
  'olives': ['black olives', 'green olives', 'kalamata olives'],
  'sun-dried tomatoes': ['sundried tomatoes', 'sun dried tomatoes'],
  'roasted red peppers': ['roasted red bell peppers', 'piquillo peppers'],

  // --- beverages / alcohol (for recipes) ---
  'white wine': ['dry white wine', 'sauvignon blanc', 'pinot grigio'],
  'red wine': ['dry red wine', 'merlot', 'cabernet sauvignon'],
  'beer': ['lager', 'pale ale'],
  'sake': ['cooking sake'],
  'mirin': ['rice wine'],
  'sherry': ['dry sherry'],

  // --- misc / global ---
  'miso': ['miso paste', 'white miso', 'red miso', 'shiro miso', 'aka miso'],
  'gochujang': ['korean chili paste', 'korean chilli paste'],
  'harissa': ['harissa paste'],
  'curry paste': ['thai curry paste', 'red curry paste', 'green curry paste', 'massaman curry paste'],
  'curry powder': [],
  'nori': ['seaweed sheets', 'sushi nori'],
  'wakame': ['dried seaweed'],
  'gelatin': ['gelatine', 'gelatin powder', 'leaf gelatine', 'gelatin leaves'],
  'cornflakes': ['corn flakes'],
};

// Pre-compute alias → canonical map for O(1) lookups
const ALIAS_TO_CANONICAL = new Map();
for (const [canonical, aliases] of Object.entries(SYNONYMS)) {
  ALIAS_TO_CANONICAL.set(canonical, canonical);
  for (const alias of aliases) {
    ALIAS_TO_CANONICAL.set(alias.toLowerCase(), canonical);
  }
}

const QUALIFIERS = /\b(fresh|frozen|dried|organic|large|small|medium|extra|chopped|sliced|diced|minced|ground|whole|raw|cooked|leftover|cold|hot|ripe|unripe|jumbo|mini|baby|young|old|mature|packed|boneless|skinless|lean|fatty|thick|thin|light|dark)\b/gi;
const UNITS = /\b(cups?|tbsp|tablespoons?|tsp|teaspoons?|oz|ounces?|lbs?|pounds?|grams?|g|kg|ml|l|liters?|pinch|dash|cans?|bottles?|jars?|bags?|packs?|boxes?|slices?|cloves?|heads?|bunches?|sprigs?)\b/gi;

function lightNormalize(s) {
  return String(s || '')
    .toLowerCase()
    // Fold diacritics so "jalapeño" -> "jalapeno", "crème fraîche" -> "creme fraiche" etc.
    // Without this, the ASCII-only strip below destroys accented characters and breaks matches.
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/\([^)]*\)/g, ' ')
    .replace(/[\d./,&]+/g, ' ')
    .replace(UNITS, ' ')
    .replace(/[^a-z\s'-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function heavyNormalize(s) {
  return lightNormalize(s).replace(QUALIFIERS, ' ').replace(/\s+/g, ' ').trim();
}

// Precompile alias regexes ONCE at module load — huge CPU win vs recompiling every call.
function escapeRegex(s) {
  return s.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
}
const SORTED_ALIASES = [...ALIAS_TO_CANONICAL.keys()]
  .filter(a => a.length >= 4)
  .sort((a, b) => b.length - a.length);
const ALIAS_REGEXES = SORTED_ALIASES.map(a => ({
  alias: a,
  re: new RegExp(`\\b${escapeRegex(a)}\\b`),
  canonical: ALIAS_TO_CANONICAL.get(a),
}));

/**
 * Canonicalize an ingredient name. Returns the canonical form if known,
 * otherwise the normalized name (stripped of qualifiers + units).
 *
 * Fast paths first — the expensive regex scan only runs for truly novel strings.
 * Inputs that are already canonical (from DB) hit step 1 and exit in O(1).
 */
export function canonicalize(name) {
  if (!name) return '';
  const light = lightNormalize(name);
  if (!light) return '';

  // 1. Direct hit on light-normalized form (fast path, O(1))
  const hit1 = ALIAS_TO_CANONICAL.get(light);
  if (hit1) return hit1;

  // 2. Direct hit on heavy-normalized form (qualifiers stripped)
  const heavy = heavyNormalize(name);
  if (heavy && heavy !== light) {
    const hit2 = ALIAS_TO_CANONICAL.get(heavy);
    if (hit2) return hit2;
  }

  // 3. Scan precompiled regexes as substrings. Only runs for novel inputs.
  for (const { re, canonical } of ALIAS_REGEXES) {
    if (re.test(heavy) || re.test(light)) return canonical;
  }

  // 4. Fall through: return heavy-normalized string (best-effort canonical)
  return heavy || light;
}

export { SYNONYMS };
