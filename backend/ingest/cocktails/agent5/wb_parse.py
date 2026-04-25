import re,json
TI={"kahlua":"Kahlua","baileys":"Baileys Irish Cream","grand marnier":"Grand Marnier","cointreau":"Cointreau","triple sec":"Triple Sec","blue curacao":"Blue Curacao","curacao":"Curacao","vodka":"Vodka","gin":"Gin","rum":"Rum","white rum":"White Rum","dark rum":"Dark Rum","spiced rum":"Spiced Rum","tequila":"Tequila","whiskey":"Whiskey","whisky":"Whisky","bourbon":"Bourbon","rye":"Rye Whiskey","scotch":"Scotch","brandy":"Brandy","cognac":"Cognac","vermouth":"Vermouth","dry vermouth":"Dry Vermouth","sweet vermouth":"Sweet Vermouth","campari":"Campari","aperol":"Aperol","amaretto":"Amaretto","sambuca":"Sambuca","absinthe":"Absinthe","chartreuse":"Chartreuse","benedictine":"Benedictine","drambuie":"Drambuie","galliano":"Galliano","frangelico":"Frangelico","midori":"Midori","jagermeister":"Jagermeister","champagne":"Champagne","prosecco":"Prosecco","wine":"Wine","beer":"Beer","sake":"Sake","angostura":"Angostura Bitters","bitters":"Bitters","grenadine":"Grenadine","simple syrup":"Simple Syrup","orgeat":"Orgeat","lime juice":"Lime Juice","lemon juice":"Lemon Juice","orange juice":"Orange Juice","pineapple juice":"Pineapple Juice","cranberry juice":"Cranberry Juice","tomato juice":"Tomato Juice","soda water":"Soda Water","tonic water":"Tonic Water","ginger ale":"Ginger Ale","ginger beer":"Ginger Beer","cola":"Cola","7-up":"7-Up","sprite":"Sprite","milk":"Milk","cream":"Cream","coconut cream":"Cream of Coconut","coconut milk":"Coconut Milk","egg":"Egg","egg white":"Egg White","sugar":"Sugar","honey":"Honey","salt":"Salt","ice":"Ice","shot":"Shot"}
ALCOHOL_KW=["vodka","gin","rum","tequila","whisk","bourbon","scotch","brandy","cognac","liqueur","campari","aperol","vermouth","absinthe","sherry","port","sake","wine","beer","champagne","prosecco","sambuca","chartreuse","benedictine","drambuie","kahlua","amaretto","midori","galliano","frangelico","baileys","grand marnier","cointreau","triple sec","curacao","jagermeister","angostura","bitters"]

def aisle(n):
    nl=n.lower()
    if any(k in nl for k in ["juice","syrup","grenadine","orgeat","honey","sugar","soda","tonic","ginger","cola","sprite","7-up","salt","water"]): return "pantry"
    if any(k in nl for k in ["milk","cream","egg"]): return "dairy"
    if any(k in nl for k in ["lime","lemon","orange","pineapple","cranberry","tomato","mint","cherry"]): return "produce"
    return "bar"

def parse_ings(wt):
    m=re.search(r"==\s*Ingredients?\s*==(.*?)(?=^==|\Z)", wt, re.S|re.M)
    if not m: return []
    body=m.group(1)
    out=[]
    for ln in body.splitlines():
        ln=ln.strip()
        if not ln or not ln.startswith("*"): continue
        ln=ln.lstrip("*").strip()
        if ln.lower().startswith("none") or ln.lower().startswith("garnish"): continue
        tm=re.match(r"^\{\{([^|}]+?)(?:\|([^}]+))?\}\}\s*(.*)", ln)
        if tm:
            tn=tm.group(1).strip().lower()
            arg=tm.group(2) or ""
            extra=tm.group(3) or ""
            name=TI.get(tn, tn.title())
            full=(arg+" "+extra).strip()
            qty=None; unit=None
            qm=re.match(r"^(\d+(?:\.\d+)?)(?:\s*(oz|ml|cl|tsp|tbsp|dash|drop|splash|cup|part))?", full)
            if qm:
                qty=float(qm.group(1))
                unit=qm.group(2)
            out.append({"name":name,"quantity":qty,"unit":unit,"aisle":aisle(name)})
        else:
            ln=re.sub(r"\[\[([^\[\]|]+)\|([^\[\]]+)\]\]", r"", ln)
            ln=re.sub(r"\[\[([^\[\]]+)\]\]", r"", ln)
            ln=re.sub(r"\{\{[^}]*\}\}","",ln)
            ln=ln.strip(" .,;:")
            if not ln or len(ln)>120: continue
            out.append({"name":ln,"quantity":None,"unit":None,"aisle":aisle(ln)})
    return out

def parse_proc(wt):
    m=re.search(r"==\s*Procedure\s*==(.*?)(?=^==|\Z)", wt, re.S|re.M)
    if not m: return []
    body=m.group(1)
    steps=[]
    for ln in body.splitlines():
        ln=ln.strip()
        if not ln: continue
        if ln.startswith("#") or ln.startswith("*"):
            ln=ln.lstrip("#*").strip()
            ln=re.sub(r"\[\[([^\[\]|]+)\|([^\[\]]+)\]\]", r"", ln)
            ln=re.sub(r"\[\[([^\[\]]+)\]\]", r"", ln)
            ln=re.sub(r"\{\{[^}]*\}\}","",ln)
            ln=ln.strip(" .")
            if ln: steps.append(ln)
    return steps

def parse_glass(wt):
    m=re.search(r"==\s*Glass\s*==(.*?)(?=^==|\Z)", wt, re.S|re.M)
    if not m: return None
    body=m.group(1).strip()
    if not body: return None
    line=body.splitlines()[0]
    line=re.sub(r"\[\[([^\[\]|]+)\|([^\[\]]+)\]\]", r"", line)
    line=re.sub(r"\[\[([^\[\]]+)\]\]", r"", line)
    line=line.lstrip("*#").strip().lower()
    for k,v in [("shot","shot"),("rocks","rocks"),("old fashion","rocks"),("highball","highball"),("collins","collins"),("martini","coupe"),("cocktail","coupe"),("coupe","coupe"),("flute","flute"),("hurricane","hurricane"),("margarita","coupe"),("snifter","snifter"),("pousse","cordial"),("liqueur","cordial"),("wine","wine"),("pint","pint"),("mug","mug")]:
        if k in line: return v
    return None

def is_alc(ings):
    for i in ings:
        n=i["name"].lower()
        if any(k in n for k in ALCOHOL_KW): return 1
    return 0

def build(title, wt):
    ings=parse_ings(wt)
    if not ings or len(ings)<1: return None
    proc=parse_proc(wt)
    if not proc: proc=["Combine ingredients with ice","Shake or stir as appropriate","Strain into chilled glass","Garnish and serve"]
    glass=parse_glass(wt) or "rocks"
    isa=is_alc(ings)
    short=title.split("/")[-1]
    return {
        "title":short,
        "content_type":"cocktail" if isa else "mocktail",
        "is_alcoholic":isa,
        "is_historic":0,
        "source_book":"Wikibooks Bartending (CC-BY-SA)",
        "source_url":"https://en.wikibooks.org/wiki/"+title.replace(" ","_"),
        "cuisine":"cocktail",
        "description":None,
        "servings":1,
        "prep_minutes":3,
        "cook_minutes":0,
        "instructions":proc,
        "ingredients":ings,
        "glass_type":glass,
        "method":"shaken",
        "garnish":None,
        "abv_percent":None if isa else 0,
        "image_url":None,
    }
