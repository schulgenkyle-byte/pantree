import re,json
GLASS_MAP={"old-fashioned glass":"rocks","rocks glass":"rocks","whiskey glass":"rocks","whiskey sour glass":"rocks","cocktail glass":"coupe","martini glass":"coupe","coupe glass":"coupe","margarita/coupette glass":"coupe","margarita glass":"coupe","coupette glass":"coupe","champagne flute":"flute","highball glass":"highball","collins glass":"collins","hurricane glass":"hurricane","pint glass":"pint","beer glass":"pint","beer mug":"pint","beer pilsner":"pilsner","pilsner glass":"pilsner","shot glass":"shot","pousse cafe glass":"cordial","cordial glass":"cordial","punch bowl":"punch","pitcher":"pitcher","jar":"jar","mason jar":"jar","wine glass":"wine","white wine glass":"wine","balloon glass":"snifter","brandy snifter":"snifter","irish coffee cup":"mug","coffee mug":"mug","copper mug":"mug","tea cup":"mug","parfait glass":"parfait","nick and nora glass":"coupe"}
PRODUCE=re.compile(r"(lemon|lime|orange|grapefruit|cherry|strawberry|raspberry|blueberry|pineapple|apple|pear|peach|melon|berry|mint|basil|cucumber|celery|peel|zest|fruit|herb)",re.I)
DAIRY=re.compile(r"(milk|cream|butter|yogurt|egg)",re.I)
PANTRY=re.compile(r"(sugar|honey|syrup|salt|pepper|spice|cinnamon|nutmeg|cocoa|chocolate|coffee|tea|vanilla|almond|grenadine|water|soda|tonic|cola|sprite|7up|ginger ale|ginger beer|juice)",re.I)
BAR=re.compile(r"(vodka|gin|rum|tequila|whiskey|whisky|bourbon|scotch|rye|cognac|brandy|vermouth|liqueur|amaretto|kahlua|baileys|campari|aperol|bitters|chartreuse|absinthe|sambuca|schnapps|wine|champagne|prosecco|beer|ale|stout|sake|triple sec|cointreau|grand marnier|port|sherry|midori|jagermeister|drambuie|frangelico|galliano|pernod|ouzo|grappa|pisco|cachaca|mezcal|aquavit|punsch|maraschino|curacao|fernet|byrrh|chambord)",re.I)
SPIRIT_RE=re.compile(r"(vodka|gin|rum|tequila|whiskey|whisky|bourbon|scotch|rye|cognac|brandy|absinthe|mezcal|cachaca|pisco|aquavit|sake|grappa)",re.I)
FORTIFIED_RE=re.compile(r"(vermouth|sherry|port|chartreuse|campari|aperol|liqueur|amaretto|kahlua|baileys|sambuca|schnapps|triple sec|cointreau|grand marnier|midori|drambuie|frangelico|galliano|pernod|ouzo|jagermeister|curacao|maraschino|fernet|chambord)",re.I)
GARNISH_RE=re.compile(r"(peel|zest|wedge|slice|twist|cherry|olive|mint|basil|sprig|leaf|leaves|salt rim|sugar rim|rim)",re.I)
QTY_RE=re.compile(r"^(\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?)\s*(.*)$")

def aisle(n):
    if BAR.search(n): return "bar"
    if PRODUCE.search(n): return "produce"
    if DAIRY.search(n): return "dairy"
    if PANTRY.search(n): return "pantry"
    return "pantry"

def parse_qty(m):
    if not m: return None,None
    m=str(m).strip()
    if not m: return None,None
    m=re.sub(r"^(juice of|top with|fill with)\s+","",m,flags=re.I)
    qty=None; rest=m
    mt=QTY_RE.match(m)
    if mt:
        np=mt.group(1); rest=mt.group(2) or ""
        if " " in np:
            w,fr=np.split(); n,d=fr.split("/"); qty=float(w)+float(n)/float(d)
        elif "/" in np:
            n,d=np.split("/"); qty=float(n)/float(d)
        else: qty=float(np)
    u=(rest or "").strip().lower()
    u=re.sub(r"\.$","",u); u=re.sub(r"s$","",u)
    if re.match(r"^oz",u) or re.match(r"^fl\.?\s*oz",u): u="oz"
    elif re.match(r"^cl",u):
        if qty is not None: qty=round(qty*0.34,2)
        u="oz"
    elif re.match(r"^ml",u):
        if qty is not None: qty=round(qty/30.0,2)
        u="oz"
    elif re.match(r"^tsp|^teaspoon",u): u="tsp"
    elif re.match(r"^tbsp|^tablespoon",u): u="tbsp"
    elif re.match(r"^dash|^dashe",u): u="dash"
    elif re.match(r"^splash",u): u="splash"
    elif re.match(r"^cup",u): u="cup"
    elif re.match(r"^shot|^jigger",u):
        u="oz"
        if qty is not None: qty=qty*1.5
    elif re.match(r"^part",u): u="part"
    elif re.match(r"^slice",u): u="slice"
    elif re.match(r"^wedge",u): u="wedge"
    elif re.match(r"^leaf|^leave",u): u="leaf"
    elif re.match(r"^sprig",u): u="sprig"
    elif re.match(r"^cube",u): u="cube"
    elif re.match(r"^drop",u): u="drop"
    elif re.match(r"^bottle",u): u="bottle"
    elif re.match(r"^can",u): u="can"
    elif not u: u="piece" if qty is not None else None
    return qty,(u or None)

def estimate_abv(ings):
    s=fo=t=0.0
    for i in ings:
        oz=i["quantity"] if i.get("unit")=="oz" and isinstance(i.get("quantity"),(int,float)) else 0
        t+=oz
        if SPIRIT_RE.search(i["name"]): s+=oz
        elif FORTIFIED_RE.search(i["name"]): fo+=oz
    dil=0.75 if t>0 else 0
    den=t+dil
    if den<=0: return None
    abv=(s*0.4+fo*0.18)/den*100
    if abv<=0: return None
    return round(abv)

def split_instr(raw):
    if not raw: return []
    c=str(raw).replace(chr(13),"").strip()
    lines=[s.strip() for s in c.split(chr(10)) if s.strip()]
    out=[]
    for line in lines:
        parts=re.split(r"(?<=[.!?])\s+(?=[A-Z])",line)
        parts=[p.strip() for p in parts if p.strip()]
        out.extend(parts if parts else [line])
    return [s.rstrip(".") for s in out if s]

def build_ings(d):
    out=[]
    for i in range(1,16):
        n=d.get(f"strIngredient{i}"); m=d.get(f"strMeasure{i}")
        if not n or not str(n).strip(): break
        cn=str(n).strip()
        q,u=parse_qty(m)
        out.append({"name":cn,"quantity":q,"unit":u,"aisle":aisle(cn)})
    return out

def desc(d):
    cat=d.get("strCategory") or ""; gl=d.get("strGlass") or ""; iba=d.get("strIBA")
    b=[]
    if iba: b.append(f"IBA {iba}")
    if cat: b.append(cat)
    if gl: b.append(f"served in a {gl.lower()}")
    return " - ".join(b)

def gn(ings,instr):
    for i in reversed(ings):
        if GARNISH_RE.search(i["name"]): return i["name"].lower()
    m=re.search(r"garnish(?:ed)?\s+with\s+([^.;]+)", instr or "", re.I)
    if m: return m.group(1).strip().lower().rstrip(".")
    return None

def method(instr):
    t=(instr or "").lower()
    if "shake" in t: return "shaken"
    if "blend" in t: return "blended"
    if "muddle" in t: return "built"
    if "stir" in t: return "stirred"
    if "build" in t or "pour" in t: return "built"
    return "built"

def normalize(d):
    title=(d.get("strDrink") or "").strip()
    instr=d.get("strInstructions") or ""
    instructions=split_instr(instr)
    if not title: return None,"empty title"
    if not instructions: return None,"empty instructions"
    alc=(d.get("strAlcoholic") or "").strip().lower()
    isa=alc=="alcoholic"
    ings=build_ings(d)
    if not ings: return None,"no ingredients"
    return {
        "title":title,
        "content_type":"cocktail" if isa else "mocktail",
        "is_alcoholic":1 if isa else 0,
        "is_historic":0,
        "source_book":"TheCocktailDB",
        "source_url":"https://www.thecocktaildb.com/drink/"+str(d.get("idDrink") or ""),
        "cuisine":"cocktail",
        "description":desc(d),
        "servings":1,
        "prep_minutes":3,
        "cook_minutes":0,
        "instructions":instructions,
        "ingredients":ings,
        "glass_type":GLASS_MAP.get(str(d.get("strGlass") or "").strip().lower(),"rocks") if d.get("strGlass") else "rocks",
        "method":method(instr),
        "garnish":gn(ings,instr),
        "abv_percent":estimate_abv(ings) if isa else 0,
        "image_url":d.get("strDrinkThumb") or None,
    },None
