-- Migrasi nama tampilan mob ke bahasa Indonesia.
-- Dihasilkan otomatis; hanya nama yang SELURUH katanya dikenal
-- glosarium yang diubah. MobIdentifier TIDAK disentuh — itu kunci
-- yang dipakai skrip Lua.
USE `RTK`;

UPDATE `Mobs` SET `MobDescription` = 'Kelinci' WHERE `MobId` = 1; -- Rabbit
UPDATE `Mobs` SET `MobDescription` = 'Tupai' WHERE `MobId` = 2; -- Squirrel
UPDATE `Mobs` SET `MobDescription` = 'Rusa' WHERE `MobId` = 3; -- Deer
UPDATE `Mobs` SET `MobDescription` = 'Rusa Betina' WHERE `MobId` = 4; -- Doe
UPDATE `Mobs` SET `MobDescription` = 'Monyet Biasa' WHERE `MobId` = 5; -- Plain monkey
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Putih' WHERE `MobId` = 6; -- White Rabbit
UPDATE `Mobs` SET `MobDescription` = 'Ayam Jantan Biru' WHERE `MobId` = 7; -- Blue rooster
UPDATE `Mobs` SET `MobDescription` = 'Kuda' WHERE `MobId` = 8; -- Horse
UPDATE `Mobs` SET `MobDescription` = 'Serigala' WHERE `MobId` = 9; -- Wolf
UPDATE `Mobs` SET `MobDescription` = 'Tikus' WHERE `MobId` = 10; -- Rat
UPDATE `Mobs` SET `MobDescription` = 'Kucing' WHERE `MobId` = 11; -- Cat
UPDATE `Mobs` SET `MobDescription` = 'Monyet Licik' WHERE `MobId` = 12; -- Sly monkey
UPDATE `Mobs` SET `MobDescription` = 'Monyet Gesit' WHERE `MobId` = 13; -- Spry monkey
UPDATE `Mobs` SET `MobDescription` = 'Monyet Buruk Rupa' WHERE `MobId` = 14; -- Ugly monkey
UPDATE `Mobs` SET `MobDescription` = 'Tikus Keji' WHERE `MobId` = 16; -- Vile rat
UPDATE `Mobs` SET `MobDescription` = 'Tikus Darah' WHERE `MobId` = 17; -- Blood rat
UPDATE `Mobs` SET `MobDescription` = 'Tikus Pembunuh' WHERE `MobId` = 18; -- Killer rat
UPDATE `Mobs` SET `MobDescription` = 'Banteng Besar' WHERE `MobId` = 19; -- Large bull
UPDATE `Mobs` SET `MobDescription` = 'Banteng Mengamuk' WHERE `MobId` = 20; -- Raging bull
UPDATE `Mobs` SET `MobDescription` = 'Sapi Menyeruduk' WHERE `MobId` = 21; -- Charging ox
UPDATE `Mobs` SET `MobDescription` = 'Anak Babi Keras Kepala' WHERE `MobId` = 22; -- Stubborn piglet
UPDATE `Mobs` SET `MobDescription` = 'Babi Keras Kepala' WHERE `MobId` = 23; -- Stubborn pig
UPDATE `Mobs` SET `MobDescription` = 'Babi Gemuk' WHERE `MobId` = 24; -- Fat pig
UPDATE `Mobs` SET `MobDescription` = 'Babi Kutil Keras Kepala' WHERE `MobId` = 25; -- Stubborn warthog
UPDATE `Mobs` SET `MobDescription` = 'Anjing Ganas' WHERE `MobId` = 26; -- Fierce dog
UPDATE `Mobs` SET `MobDescription` = 'Anjing Petarung' WHERE `MobId` = 27; -- Fighting dog
UPDATE `Mobs` SET `MobDescription` = 'Anjing Gila' WHERE `MobId` = 28; -- Mad dog
UPDATE `Mobs` SET `MobDescription` = 'Anjing Pucat' WHERE `MobId` = 29; -- Pale dog
UPDATE `Mobs` SET `MobDescription` = 'Anak Ayam Hitam' WHERE `MobId` = 30; -- Black chick
UPDATE `Mobs` SET `MobDescription` = 'Ayam Liar' WHERE `MobId` = 31; -- Wild chicken
UPDATE `Mobs` SET `MobDescription` = 'Ayam Jantan Liar' WHERE `MobId` = 32; -- Wild rooster
UPDATE `Mobs` SET `MobDescription` = 'Anak Ayam Bercahaya' WHERE `MobId` = 33; -- Radiant chick
UPDATE `Mobs` SET `MobDescription` = 'Ular Biasa' WHERE `MobId` = 34; -- Plain snake
UPDATE `Mobs` SET `MobDescription` = 'Ular Darah' WHERE `MobId` = 35; -- Blood snake
UPDATE `Mobs` SET `MobDescription` = 'Ular Bercahaya' WHERE `MobId` = 36; -- Radiant snake
UPDATE `Mobs` SET `MobDescription` = 'Ular Api' WHERE `MobId` = 37; -- Fire snake
UPDATE `Mobs` SET `MobDescription` = 'Kuda Liar' WHERE `MobId` = 38; -- Wild horse
UPDATE `Mobs` SET `MobDescription` = 'Naga Muda' WHERE `MobId` = 45; -- Young dragon
UPDATE `Mobs` SET `MobDescription` = 'Domba Ganas' WHERE `MobId` = 46; -- Fierce Sheep
UPDATE `Mobs` SET `MobDescription` = 'Domba Buruk Rupa' WHERE `MobId` = 47; -- Ugly Sheep
UPDATE `Mobs` SET `MobDescription` = 'Binatang Es' WHERE `MobId` = 48; -- Ice Beast
UPDATE `Mobs` SET `MobDescription` = 'Mencit' WHERE `MobId` = 49; -- Mouse
UPDATE `Mobs` SET `MobDescription` = 'Tikus Putih' WHERE `MobId` = 50; -- White rat
UPDATE `Mobs` SET `MobDescription` = 'Kelabang' WHERE `MobId` = 51; -- Centipede
UPDATE `Mobs` SET `MobDescription` = 'Mencit Ganas' WHERE `MobId` = 52; -- Fierce mouse
UPDATE `Mobs` SET `MobDescription` = 'Kelelawar' WHERE `MobId` = 53; -- Bat
UPDATE `Mobs` SET `MobDescription` = 'Kelelawar Besar' WHERE `MobId` = 54; -- Big bat
UPDATE `Mobs` SET `MobDescription` = 'Tikus Besar' WHERE `MobId` = 55; -- Big rat
UPDATE `Mobs` SET `MobDescription` = 'Tupai Perak' WHERE `MobId` = 56; -- Silver squirrel
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Emas' WHERE `MobId` = 57; -- Golden rabbit
UPDATE `Mobs` SET `MobDescription` = 'Rubah' WHERE `MobId` = 70; -- Fox
UPDATE `Mobs` SET `MobDescription` = 'Domba' WHERE `MobId` = 71; -- Sheep
UPDATE `Mobs` SET `MobDescription` = 'Ular' WHERE `MobId` = 72; -- Snake
UPDATE `Mobs` SET `MobDescription` = 'Rusa Jantan Hitam' WHERE `MobId` = 73; -- Black buck
UPDATE `Mobs` SET `MobDescription` = 'Serigala Darah' WHERE `MobId` = 74; -- Blood wolf
UPDATE `Mobs` SET `MobDescription` = 'Rubah Gelap' WHERE `MobId` = 75; -- Dark fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Darah' WHERE `MobId` = 76; -- Blood fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Lava' WHERE `MobId` = 77; -- Lava fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Matahari' WHERE `MobId` = 78; -- Sun fox
UPDATE `Mobs` SET `MobDescription` = 'Gagak' WHERE `MobId` = 80; -- Raven
UPDATE `Mobs` SET `MobDescription` = 'Rubah Berekor Sembilan' WHERE `MobId` = 81; -- Nine-tailed fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Berekor Sembilan' WHERE `MobId` = 82; -- Nine-tailed fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Berekor Sembilan' WHERE `MobId` = 83; -- Nine-tailed fox
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Monyet' WHERE `MobId` = 87; -- Monkey sentry
UPDATE `Mobs` SET `MobDescription` = 'Veteran Domba' WHERE `MobId` = 88; -- Sheep veteran
UPDATE `Mobs` SET `MobDescription` = 'Penyihir' WHERE `MobId` = 91; -- Witch
UPDATE `Mobs` SET `MobDescription` = 'Dukun Penyihir' WHERE `MobId` = 93; -- Witch shaman
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Kerangka' WHERE `MobId` = 95; -- Skeleton mage
UPDATE `Mobs` SET `MobDescription` = 'Prajurit Kerangka' WHERE `MobId` = 96; -- Skeleton warrior
UPDATE `Mobs` SET `MobDescription` = 'Kelabang Raksasa' WHERE `MobId` = 97; -- Giant centipede
UPDATE `Mobs` SET `MobDescription` = 'Kelabang Besar' WHERE `MobId` = 98; -- Large centipede
UPDATE `Mobs` SET `MobDescription` = 'Laba-laba Raksasa' WHERE `MobId` = 100; -- Giant spider
UPDATE `Mobs` SET `MobDescription` = 'Kalajengking Raksasa' WHERE `MobId` = 101; -- Giant scorpion
UPDATE `Mobs` SET `MobDescription` = 'Laba-laba Bercahaya' WHERE `MobId` = 103; -- Radiant spider
UPDATE `Mobs` SET `MobDescription` = 'Kalajengking Pucat' WHERE `MobId` = 104; -- Pale scorpion
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Domba' WHERE `MobId` = 106; -- Sheep sentry
UPDATE `Mobs` SET `MobDescription` = 'Anak Babi Liar' WHERE `MobId` = 107; -- Wild piglet
UPDATE `Mobs` SET `MobDescription` = 'Cacing' WHERE `MobId` = 108; -- Worm
UPDATE `Mobs` SET `MobDescription` = 'Babi Liar' WHERE `MobId` = 109; -- Wild pig
UPDATE `Mobs` SET `MobDescription` = 'Ular Lumpur' WHERE `MobId` = 110; -- Mud snake
UPDATE `Mobs` SET `MobDescription` = 'Cacing Api' WHERE `MobId` = 111; -- Fire worm
UPDATE `Mobs` SET `MobDescription` = 'Banteng Lumpur' WHERE `MobId` = 112; -- Mud bull
UPDATE `Mobs` SET `MobDescription` = 'Ular Bumi' WHERE `MobId` = 113; -- Earth snake
UPDATE `Mobs` SET `MobDescription` = 'Babi Kutil' WHERE `MobId` = 114; -- Warthog
UPDATE `Mobs` SET `MobDescription` = 'Terwelu' WHERE `MobId` = 116; -- Hare
UPDATE `Mobs` SET `MobDescription` = 'Rubah Berekor Sembilan' WHERE `MobId` = 118; -- Nine-tailed fox
UPDATE `Mobs` SET `MobDescription` = 'Makhluk Aneh' WHERE `MobId` = 119; -- Strange thing
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Sapi' WHERE `MobId` = 122; -- Ox sentry
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Babi Hutan' WHERE `MobId` = 123; -- Boar sentry
UPDATE `Mobs` SET `MobDescription` = 'Jawara Babi Hutan' WHERE `MobId` = 125; -- Boar champion
UPDATE `Mobs` SET `MobDescription` = 'Serigala Gelap' WHERE `MobId` = 127; -- Dark wolf
UPDATE `Mobs` SET `MobDescription` = 'Terwelu Besar' WHERE `MobId` = 128; -- Large hare
UPDATE `Mobs` SET `MobDescription` = 'Terwelu Merah' WHERE `MobId` = 130; -- Red hare
UPDATE `Mobs` SET `MobDescription` = 'Belalang Sembah Raksasa' WHERE `MobId` = 131; -- Giant mantis
UPDATE `Mobs` SET `MobDescription` = 'Belalang Sembah Darah' WHERE `MobId` = 132; -- Blood mantis
UPDATE `Mobs` SET `MobDescription` = 'Rusa Jantan Gelap' WHERE `MobId` = 135; -- Dark buck
UPDATE `Mobs` SET `MobDescription` = 'Rusa Gelap' WHERE `MobId` = 136; -- Dark deer
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Kuda' WHERE `MobId` = 138; -- Horse sentry
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Anjing' WHERE `MobId` = 145; -- Dog sentry
UPDATE `Mobs` SET `MobDescription` = 'Pembunuh Anjing' WHERE `MobId` = 147; -- Dog assassin
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Tikus' WHERE `MobId` = 148; -- Rat sentry
UPDATE `Mobs` SET `MobDescription` = 'Ogre Beku' WHERE `MobId` = 150; -- Frost ogre
UPDATE `Mobs` SET `MobDescription` = 'Ogre Es' WHERE `MobId` = 152; -- Ice ogre
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Naga' WHERE `MobId` = 161; -- Dragon sentry
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Naga' WHERE `MobId` = 162; -- Dragon mage
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Ular' WHERE `MobId` = 164; -- Snake sentry
UPDATE `Mobs` SET `MobDescription` = 'Serigala Putih' WHERE `MobId` = 165; -- White wolf
UPDATE `Mobs` SET `MobDescription` = 'Mencit Ilahi' WHERE `MobId` = 166; -- Divine mouse
UPDATE `Mobs` SET `MobDescription` = 'Tikus Lumpur' WHERE `MobId` = 167; -- Mud rat
UPDATE `Mobs` SET `MobDescription` = 'Tikus Lava' WHERE `MobId` = 169; -- Lava rat
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Tikus' WHERE `MobId` = 170; -- Rat guardian
UPDATE `Mobs` SET `MobDescription` = 'Mencit Roh' WHERE `MobId` = 171; -- Spirit mouse
UPDATE `Mobs` SET `MobDescription` = 'Tikus Bumi' WHERE `MobId` = 172; -- Earth rat
UPDATE `Mobs` SET `MobDescription` = 'Tikus Api' WHERE `MobId` = 173; -- Fire rat
UPDATE `Mobs` SET `MobDescription` = 'Pembela Tikus' WHERE `MobId` = 175; -- Rat defender
UPDATE `Mobs` SET `MobDescription` = 'Mencit Perkasa' WHERE `MobId` = 177; -- Mighty mouse
UPDATE `Mobs` SET `MobDescription` = 'Tikus Ilahi' WHERE `MobId` = 178; -- Divine rat
UPDATE `Mobs` SET `MobDescription` = 'Tikus Roh' WHERE `MobId` = 180; -- Spirit rat
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Tikus' WHERE `MobId` = 181; -- Rat avenger
UPDATE `Mobs` SET `MobDescription` = 'Tupai Hijau' WHERE `MobId` = 185; -- Green squirrel
UPDATE `Mobs` SET `MobDescription` = 'Rusa Biru' WHERE `MobId` = 186; -- Blue deer
UPDATE `Mobs` SET `MobDescription` = 'Rusa Betina Biru' WHERE `MobId` = 187; -- Blue doe
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Biru' WHERE `MobId` = 188; -- Blue rabbit
UPDATE `Mobs` SET `MobDescription` = 'Rusa Hijau' WHERE `MobId` = 189; -- Green deer
UPDATE `Mobs` SET `MobDescription` = 'Rusa Betina Hijau' WHERE `MobId` = 190; -- Green doe
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Hijau' WHERE `MobId` = 191; -- Green rabbit
UPDATE `Mobs` SET `MobDescription` = 'Rusa Jingga' WHERE `MobId` = 192; -- Orange deer
UPDATE `Mobs` SET `MobDescription` = 'Rusa Betina Jingga' WHERE `MobId` = 193; -- Orange doe
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Jingga' WHERE `MobId` = 194; -- Orange rabbit
UPDATE `Mobs` SET `MobDescription` = 'Rusa Merah' WHERE `MobId` = 195; -- Red deer
UPDATE `Mobs` SET `MobDescription` = 'Rusa Betina Merah' WHERE `MobId` = 196; -- Red doe
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Merah' WHERE `MobId` = 197; -- Red rabbit
UPDATE `Mobs` SET `MobDescription` = 'Mencit Buta' WHERE `MobId` = 198; -- Blind mouse
UPDATE `Mobs` SET `MobDescription` = 'Tikus Buta' WHERE `MobId` = 199; -- Blind rat
UPDATE `Mobs` SET `MobDescription` = 'Kelabang Buta' WHERE `MobId` = 201; -- Blind centipede
UPDATE `Mobs` SET `MobDescription` = 'Belalang Sembah Buta' WHERE `MobId` = 203; -- Blind mantis
UPDATE `Mobs` SET `MobDescription` = 'Harimau Gelap' WHERE `MobId` = 295; -- Dark tiger
UPDATE `Mobs` SET `MobDescription` = 'Harimau Raksasa' WHERE `MobId` = 296; -- Giant tiger
UPDATE `Mobs` SET `MobDescription` = 'Harimau Emas' WHERE `MobId` = 297; -- Golden tiger
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 305; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Rubah Pinus' WHERE `MobId` = 306; -- Pine fox
UPDATE `Mobs` SET `MobDescription` = 'Serigala Pinus' WHERE `MobId` = 307; -- Pine wolf
UPDATE `Mobs` SET `MobDescription` = 'Gagak Pinus' WHERE `MobId` = 308; -- Pine raven
UPDATE `Mobs` SET `MobDescription` = 'Sapi' WHERE `MobId` = 309; -- Cow
UPDATE `Mobs` SET `MobDescription` = 'Makhluk Aneh' WHERE `MobId` = 313; -- Strange thing
UPDATE `Mobs` SET `MobDescription` = 'Gagak' WHERE `MobId` = 314; -- Crow
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 317; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 318; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 319; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 320; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 321; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 322; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 323; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 324; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Tentara Bayaran' WHERE `MobId` = 325; -- Mercenary
UPDATE `Mobs` SET `MobDescription` = 'Monyet Sederhana' WHERE `MobId` = 326; -- Simple monkey
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Monyet' WHERE `MobId` = 330; -- Monkey guardian
UPDATE `Mobs` SET `MobDescription` = 'Monyet Ilahi' WHERE `MobId` = 331; -- Divine monkey
UPDATE `Mobs` SET `MobDescription` = 'Monyet Bersemangat' WHERE `MobId` = 335; -- Spunky monkey
UPDATE `Mobs` SET `MobDescription` = 'Pembela Monyet' WHERE `MobId` = 337; -- Monkey defender
UPDATE `Mobs` SET `MobDescription` = 'Monyet Roh' WHERE `MobId` = 338; -- Spirit monkey
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Monyet' WHERE `MobId` = 339; -- Monkey avenger
UPDATE `Mobs` SET `MobDescription` = 'Prajurit Harimau' WHERE `MobId` = 341; -- Tiger warrior
UPDATE `Mobs` SET `MobDescription` = 'Harimau Mengamuk' WHERE `MobId` = 342; -- Raging tiger
UPDATE `Mobs` SET `MobDescription` = 'Harimau Hitam' WHERE `MobId` = 343; -- Black tiger
UPDATE `Mobs` SET `MobDescription` = 'Harimau Ilahi' WHERE `MobId` = 345; -- Divine tiger
UPDATE `Mobs` SET `MobDescription` = 'Penebas Harimau' WHERE `MobId` = 346; -- Tiger slasher
UPDATE `Mobs` SET `MobDescription` = 'Harimau Roh' WHERE `MobId` = 350; -- Spirit tiger
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Harimau' WHERE `MobId` = 351; -- Tiger avenger
UPDATE `Mobs` SET `MobDescription` = 'Anak Babi Kekar' WHERE `MobId` = 352; -- Stout piglet
UPDATE `Mobs` SET `MobDescription` = 'Babi Kekar' WHERE `MobId` = 353; -- Stout pig
UPDATE `Mobs` SET `MobDescription` = 'Babi Kutil Kekar' WHERE `MobId` = 355; -- Stout warthog
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Babi' WHERE `MobId` = 356; -- Pig guardian
UPDATE `Mobs` SET `MobDescription` = 'Babi Ilahi' WHERE `MobId` = 357; -- Divine pig
UPDATE `Mobs` SET `MobDescription` = 'Jawara Babi' WHERE `MobId` = 358; -- Pig champion
UPDATE `Mobs` SET `MobDescription` = 'Anak Babi Berani' WHERE `MobId` = 359; -- Bold piglet
UPDATE `Mobs` SET `MobDescription` = 'Babi Berani' WHERE `MobId` = 360; -- Bold pig
UPDATE `Mobs` SET `MobDescription` = 'Babi Hutan Berani' WHERE `MobId` = 362; -- Bold boar
UPDATE `Mobs` SET `MobDescription` = 'Pembela Babi Hutan' WHERE `MobId` = 363; -- Boar defender
UPDATE `Mobs` SET `MobDescription` = 'Babi Roh' WHERE `MobId` = 364; -- Spirit pig
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Babi' WHERE `MobId` = 365; -- Pig avenger
UPDATE `Mobs` SET `MobDescription` = 'Banteng Raksasa' WHERE `MobId` = 366; -- Giant bull
UPDATE `Mobs` SET `MobDescription` = 'Sapi Marah' WHERE `MobId` = 368; -- Angry ox
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Sapi' WHERE `MobId` = 369; -- Ox guardian
UPDATE `Mobs` SET `MobDescription` = 'Sapi Ilahi' WHERE `MobId` = 370; -- Divine ox
UPDATE `Mobs` SET `MobDescription` = 'Pembela Sapi' WHERE `MobId` = 375; -- Ox defender
UPDATE `Mobs` SET `MobDescription` = 'Sapi Roh' WHERE `MobId` = 376; -- Spirit ox
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Sapi' WHERE `MobId` = 377; -- Ox avenger
UPDATE `Mobs` SET `MobDescription` = 'Ahli Pedang Kuda' WHERE `MobId` = 379; -- Horse swordsman
UPDATE `Mobs` SET `MobDescription` = 'Kuda Emas' WHERE `MobId` = 381; -- Golden horse
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Kuda' WHERE `MobId` = 382; -- Horse guardian
UPDATE `Mobs` SET `MobDescription` = 'Kuda Ilahi' WHERE `MobId` = 383; -- Divine horse
UPDATE `Mobs` SET `MobDescription` = 'Pembela Kuda' WHERE `MobId` = 389; -- Horse defender
UPDATE `Mobs` SET `MobDescription` = 'Kuda Roh' WHERE `MobId` = 390; -- Spirit horse
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Kuda' WHERE `MobId` = 391; -- Horse avenger
UPDATE `Mobs` SET `MobDescription` = 'Domba Hitam' WHERE `MobId` = 392; -- Black sheep
UPDATE `Mobs` SET `MobDescription` = 'Domba Merah' WHERE `MobId` = 393; -- Red sheep
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Domba' WHERE `MobId` = 394; -- Sheep guardian
UPDATE `Mobs` SET `MobDescription` = 'Domba Ilahi' WHERE `MobId` = 395; -- Divine sheep
UPDATE `Mobs` SET `MobDescription` = 'Domba Gelap' WHERE `MobId` = 398; -- Dark sheep
UPDATE `Mobs` SET `MobDescription` = 'Pembela Domba' WHERE `MobId` = 399; -- Sheep defender
UPDATE `Mobs` SET `MobDescription` = 'Domba Roh' WHERE `MobId` = 400; -- Spirit sheep
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Domba' WHERE `MobId` = 401; -- Sheep avenger
UPDATE `Mobs` SET `MobDescription` = 'Anjing Bersemangat' WHERE `MobId` = 404; -- Spunky dog
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Anjing' WHERE `MobId` = 406; -- Dog guardian
UPDATE `Mobs` SET `MobDescription` = 'Anjing Ilahi' WHERE `MobId` = 407; -- Divine dog
UPDATE `Mobs` SET `MobDescription` = 'Anjing Kampung Pembunuh' WHERE `MobId` = 411; -- Killer mongrel
UPDATE `Mobs` SET `MobDescription` = 'Pembela Anjing' WHERE `MobId` = 413; -- Dog defender
UPDATE `Mobs` SET `MobDescription` = 'Anjing Roh' WHERE `MobId` = 414; -- Spirit dog
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Anjing' WHERE `MobId` = 415; -- Dog avenger
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Naga' WHERE `MobId` = 420; -- Dragon guardian
UPDATE `Mobs` SET `MobDescription` = 'Naga Ilahi' WHERE `MobId` = 421; -- Divine dragon
UPDATE `Mobs` SET `MobDescription` = 'Pembantai Naga' WHERE `MobId` = 422; -- Dragon slayer
UPDATE `Mobs` SET `MobDescription` = 'Wyrm Agung' WHERE `MobId` = 423; -- Great wyrm
UPDATE `Mobs` SET `MobDescription` = 'Wyrm Perkasa' WHERE `MobId` = 424; -- Mighty wyrm
UPDATE `Mobs` SET `MobDescription` = 'Naga Tua' WHERE `MobId` = 425; -- Old dragon
UPDATE `Mobs` SET `MobDescription` = 'Pembela Naga' WHERE `MobId` = 426; -- Dragon defender
UPDATE `Mobs` SET `MobDescription` = 'Naga Roh' WHERE `MobId` = 427; -- Spirit dragon
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Naga' WHERE `MobId` = 428; -- Dragon avenger
UPDATE `Mobs` SET `MobDescription` = 'Dukun Ular' WHERE `MobId` = 430; -- Snake shaman
UPDATE `Mobs` SET `MobDescription` = 'Ular Sederhana' WHERE `MobId` = 432; -- Simple snake
UPDATE `Mobs` SET `MobDescription` = 'Ular Lava' WHERE `MobId` = 433; -- Lava snake
UPDATE `Mobs` SET `MobDescription` = 'Ular Gelap' WHERE `MobId` = 435; -- Dark snake
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Ular' WHERE `MobId` = 436; -- Snake guardian
UPDATE `Mobs` SET `MobDescription` = 'Ular Ilahi' WHERE `MobId` = 437; -- Divine snake
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Ular' WHERE `MobId` = 438; -- Snake mage
UPDATE `Mobs` SET `MobDescription` = 'Ular Badai' WHERE `MobId` = 442; -- Storm snake
UPDATE `Mobs` SET `MobDescription` = 'Pembela Ular' WHERE `MobId` = 443; -- Snake defender
UPDATE `Mobs` SET `MobDescription` = 'Ular Roh' WHERE `MobId` = 444; -- Spirit snake
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Ular' WHERE `MobId` = 445; -- Snake avenger
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Ayam Jantan' WHERE `MobId` = 446; -- Rooster sentry
UPDATE `Mobs` SET `MobDescription` = 'Ahli Pedang Ayam Jantan' WHERE `MobId` = 448; -- Rooster swordsman
UPDATE `Mobs` SET `MobDescription` = 'Anak Ayam Merah' WHERE `MobId` = 449; -- Red chick
UPDATE `Mobs` SET `MobDescription` = 'Ayam Putih' WHERE `MobId` = 451; -- White chicken
UPDATE `Mobs` SET `MobDescription` = 'Ayam Jantan Putih' WHERE `MobId` = 452; -- White rooster
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Ayam Jantan' WHERE `MobId` = 453; -- Rooster guardian
UPDATE `Mobs` SET `MobDescription` = 'Ayam Jantan Ilahi' WHERE `MobId` = 454; -- Divine rooster
UPDATE `Mobs` SET `MobDescription` = 'Anak Ayam Liar' WHERE `MobId` = 456; -- Wild chick
UPDATE `Mobs` SET `MobDescription` = 'Ayam Hitam' WHERE `MobId` = 457; -- Black chicken
UPDATE `Mobs` SET `MobDescription` = 'Pembela Ayam Jantan' WHERE `MobId` = 460; -- Rooster defender
UPDATE `Mobs` SET `MobDescription` = 'Ayam Jantan Roh' WHERE `MobId` = 461; -- Spirit rooster
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Ayam Jantan' WHERE `MobId` = 462; -- Rooster avenger
UPDATE `Mobs` SET `MobDescription` = 'Ogre Batu' WHERE `MobId` = 465; -- Stone ogre
UPDATE `Mobs` SET `MobDescription` = 'Belalang Sembah Bayangan' WHERE `MobId` = 468; -- Shadow Mantis
UPDATE `Mobs` SET `MobDescription` = 'Belalang Sembah Gelap' WHERE `MobId` = 469; -- Dark Mantis
UPDATE `Mobs` SET `MobDescription` = 'Belalang Sembah Hantu' WHERE `MobId` = 471; -- Ghost Mantis
UPDATE `Mobs` SET `MobDescription` = 'Harimau Besi' WHERE `MobId` = 476; -- Iron Tiger
UPDATE `Mobs` SET `MobDescription` = 'Harimau Raksasa Besi' WHERE `MobId` = 477; -- Giant Iron Tiger
UPDATE `Mobs` SET `MobDescription` = 'Kerangka Hitam' WHERE `MobId` = 479; -- Black skeleton
UPDATE `Mobs` SET `MobDescription` = 'Binatang Salju' WHERE `MobId` = 489; -- Snow beast
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Salju' WHERE `MobId` = 491; -- Snow rabbit
UPDATE `Mobs` SET `MobDescription` = 'Ogre Bubur Salju' WHERE `MobId` = 493; -- Slush ogre
UPDATE `Mobs` SET `MobDescription` = 'Ogre Salju' WHERE `MobId` = 494; -- Snow ogre
UPDATE `Mobs` SET `MobDescription` = 'Ogre Hujan Es' WHERE `MobId` = 495; -- Sleet ogre
UPDATE `Mobs` SET `MobDescription` = 'Ogre Badai Salju' WHERE `MobId` = 498; -- Blizzard ogre
UPDATE `Mobs` SET `MobDescription` = 'Ogre Prahara' WHERE `MobId` = 500; -- Tempest ogre
UPDATE `Mobs` SET `MobDescription` = 'Raja Bubur Salju' WHERE `MobId` = 502; -- Slush king
UPDATE `Mobs` SET `MobDescription` = 'Raja Salju' WHERE `MobId` = 503; -- Snow king
UPDATE `Mobs` SET `MobDescription` = 'Raja Hujan Es' WHERE `MobId` = 504; -- Sleet king
UPDATE `Mobs` SET `MobDescription` = 'Raja Badai Salju' WHERE `MobId` = 507; -- Blizzard king
UPDATE `Mobs` SET `MobDescription` = 'Raja Prahara' WHERE `MobId` = 509; -- Tempest king
UPDATE `Mobs` SET `MobDescription` = 'Beruang' WHERE `MobId` = 511; -- Bear
UPDATE `Mobs` SET `MobDescription` = 'Harimau' WHERE `MobId` = 512; -- Tiger
UPDATE `Mobs` SET `MobDescription` = 'Lobster Emas' WHERE `MobId` = 517; -- Golden Lobster
UPDATE `Mobs` SET `MobDescription` = 'Terwelu Emas' WHERE `MobId` = 518; -- Golden hare
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Gila' WHERE `MobId` = 519; -- Mad rabbit
UPDATE `Mobs` SET `MobDescription` = 'Terwelu Raksasa' WHERE `MobId` = 520; -- Giant hare
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Kelinci' WHERE `MobId` = 521; -- Rabbit sentry
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Terwelu' WHERE `MobId` = 523; -- Hare witch
UPDATE `Mobs` SET `MobDescription` = 'Terwelu Gila' WHERE `MobId` = 524; -- Mad hare
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Raksasa' WHERE `MobId` = 525; -- Giant rabbit
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Kelinci' WHERE `MobId` = 526; -- Rabbit guardian
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Ilahi' WHERE `MobId` = 527; -- Divine rabbit
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Kelinci' WHERE `MobId` = 528; -- Rabbit witch
UPDATE `Mobs` SET `MobDescription` = 'Pembela Kelinci' WHERE `MobId` = 532; -- Rabbit defender
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Roh' WHERE `MobId` = 533; -- Spirit rabbit
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Kelinci' WHERE `MobId` = 534; -- Rabbit avenger
UPDATE `Mobs` SET `MobDescription` = 'Bijih' WHERE `MobId` = 535; -- Ore
UPDATE `Mobs` SET `MobDescription` = 'Urat Perak Bijih' WHERE `MobId` = 540; -- Silver ore vein
UPDATE `Mobs` SET `MobDescription` = 'Gandum Jangkung' WHERE `MobId` = 543; -- Tall wheat
UPDATE `Mobs` SET `MobDescription` = 'Badai' WHERE `MobId` = 549; -- Storm
UPDATE `Mobs` SET `MobDescription` = 'Badai Salju' WHERE `MobId` = 550; -- Blizzard
UPDATE `Mobs` SET `MobDescription` = 'Kalajengking Keji' WHERE `MobId` = 554; -- Vile scorpion
UPDATE `Mobs` SET `MobDescription` = 'Kalajengking Bercahaya' WHERE `MobId` = 556; -- Radiant scorpion
UPDATE `Mobs` SET `MobDescription` = 'Monyet Liar' WHERE `MobId` = 565; -- Wild monkey
UPDATE `Mobs` SET `MobDescription` = 'Prajurit Angin' WHERE `MobId` = 568; -- Wind warrior
UPDATE `Mobs` SET `MobDescription` = 'Roh Muda' WHERE `MobId` = 569; -- Young spirit
UPDATE `Mobs` SET `MobDescription` = 'Roh Marah' WHERE `MobId` = 571; -- Angry spirit
UPDATE `Mobs` SET `MobDescription` = 'Roh Purba' WHERE `MobId` = 574; -- Ancient spirit
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Roh' WHERE `MobId` = 575; -- Spirit guardian
UPDATE `Mobs` SET `MobDescription` = 'Pembela Roh' WHERE `MobId` = 576; -- Spirit defender
UPDATE `Mobs` SET `MobDescription` = 'Jawara Roh' WHERE `MobId` = 577; -- Spirit champion
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Emas' WHERE `MobId` = 599; -- Golden rabbit
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Naga' WHERE `MobId` = 602; -- Dragon mage
UPDATE `Mobs` SET `MobDescription` = 'Naga Ilahi' WHERE `MobId` = 603; -- Divine dragon
UPDATE `Mobs` SET `MobDescription` = 'Pembantai Naga' WHERE `MobId` = 604; -- Dragon slayer
UPDATE `Mobs` SET `MobDescription` = 'Naga Roh' WHERE `MobId` = 605; -- Spirit dragon
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Naga' WHERE `MobId` = 606; -- Dragon avenger
UPDATE `Mobs` SET `MobDescription` = 'Pembunuh Anjing' WHERE `MobId` = 608; -- Dog assassin
UPDATE `Mobs` SET `MobDescription` = 'Anjing Ilahi' WHERE `MobId` = 609; -- Divine dog
UPDATE `Mobs` SET `MobDescription` = 'Anjing Roh' WHERE `MobId` = 611; -- Spirit dog
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Anjing' WHERE `MobId` = 612; -- Dog avenger
UPDATE `Mobs` SET `MobDescription` = 'Kuda Ilahi' WHERE `MobId` = 615; -- Divine horse
UPDATE `Mobs` SET `MobDescription` = 'Kuda Roh' WHERE `MobId` = 617; -- Spirit horse
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Kuda' WHERE `MobId` = 618; -- Horse avenger
UPDATE `Mobs` SET `MobDescription` = 'Monyet Ilahi' WHERE `MobId` = 621; -- Divine monkey
UPDATE `Mobs` SET `MobDescription` = 'Monyet Roh' WHERE `MobId` = 623; -- Spirit monkey
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Monyet' WHERE `MobId` = 624; -- Monkey avenger
UPDATE `Mobs` SET `MobDescription` = 'Sapi Ilahi' WHERE `MobId` = 627; -- Divine ox
UPDATE `Mobs` SET `MobDescription` = 'Sapi Roh' WHERE `MobId` = 629; -- Spirit ox
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Sapi' WHERE `MobId` = 630; -- Ox avenger
UPDATE `Mobs` SET `MobDescription` = 'Jawara Babi Hutan' WHERE `MobId` = 632; -- Boar champion
UPDATE `Mobs` SET `MobDescription` = 'Babi Ilahi' WHERE `MobId` = 633; -- Divine pig
UPDATE `Mobs` SET `MobDescription` = 'Jawara Babi' WHERE `MobId` = 634; -- Pig champion
UPDATE `Mobs` SET `MobDescription` = 'Babi Roh' WHERE `MobId` = 635; -- Spirit pig
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Babi' WHERE `MobId` = 636; -- Pig avenger
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Terwelu' WHERE `MobId` = 638; -- Hare witch
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Ilahi' WHERE `MobId` = 639; -- Divine rabbit
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Kelinci' WHERE `MobId` = 640; -- Rabbit witch
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Roh' WHERE `MobId` = 641; -- Spirit rabbit
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Kelinci' WHERE `MobId` = 642; -- Rabbit avenger
UPDATE `Mobs` SET `MobDescription` = 'Mencit Perkasa' WHERE `MobId` = 644; -- Mighty mouse
UPDATE `Mobs` SET `MobDescription` = 'Tikus Ilahi' WHERE `MobId` = 645; -- Divine rat
UPDATE `Mobs` SET `MobDescription` = 'Tikus Roh' WHERE `MobId` = 647; -- Spirit rat
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Tikus' WHERE `MobId` = 648; -- Rat avenger
UPDATE `Mobs` SET `MobDescription` = 'Ahli Pedang Ayam Jantan' WHERE `MobId` = 650; -- Rooster swordsman
UPDATE `Mobs` SET `MobDescription` = 'Ayam Jantan Ilahi' WHERE `MobId` = 651; -- Divine rooster
UPDATE `Mobs` SET `MobDescription` = 'Ayam Jantan Roh' WHERE `MobId` = 653; -- Spirit rooster
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Ayam Jantan' WHERE `MobId` = 654; -- Rooster avenger
UPDATE `Mobs` SET `MobDescription` = 'Domba Ilahi' WHERE `MobId` = 655; -- Divine sheep
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Domba' WHERE `MobId` = 657; -- Sheep avenger
UPDATE `Mobs` SET `MobDescription` = 'Veteran Domba' WHERE `MobId` = 659; -- Sheep veteran
UPDATE `Mobs` SET `MobDescription` = 'Domba Roh' WHERE `MobId` = 660; -- Spirit sheep
UPDATE `Mobs` SET `MobDescription` = 'Dukun Ular' WHERE `MobId` = 662; -- Snake shaman
UPDATE `Mobs` SET `MobDescription` = 'Ular Ilahi' WHERE `MobId` = 663; -- Divine snake
UPDATE `Mobs` SET `MobDescription` = 'Penyihir Ular' WHERE `MobId` = 664; -- Snake mage
UPDATE `Mobs` SET `MobDescription` = 'Ular Roh' WHERE `MobId` = 665; -- Spirit snake
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Ular' WHERE `MobId` = 666; -- Snake avenger
UPDATE `Mobs` SET `MobDescription` = 'Prajurit Harimau' WHERE `MobId` = 668; -- Tiger warrior
UPDATE `Mobs` SET `MobDescription` = 'Harimau Ilahi' WHERE `MobId` = 669; -- Divine tiger
UPDATE `Mobs` SET `MobDescription` = 'Penebas Harimau' WHERE `MobId` = 670; -- Tiger slasher
UPDATE `Mobs` SET `MobDescription` = 'Harimau Roh' WHERE `MobId` = 671; -- Spirit tiger
UPDATE `Mobs` SET `MobDescription` = 'Pembalas Harimau' WHERE `MobId` = 672; -- Tiger avenger
UPDATE `Mobs` SET `MobDescription` = 'Rubah' WHERE `MobId` = 673; -- Fox
UPDATE `Mobs` SET `MobDescription` = 'Domba' WHERE `MobId` = 674; -- Sheep
UPDATE `Mobs` SET `MobDescription` = 'Rubah Gelap' WHERE `MobId` = 675; -- Dark fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Darah' WHERE `MobId` = 676; -- Blood fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Lava' WHERE `MobId` = 677; -- Lava fox
UPDATE `Mobs` SET `MobDescription` = 'Rubah Matahari' WHERE `MobId` = 678; -- Sun fox
UPDATE `Mobs` SET `MobDescription` = 'Beruang' WHERE `MobId` = 679; -- Bear
UPDATE `Mobs` SET `MobDescription` = 'Tikus' WHERE `MobId` = 680; -- Rat
UPDATE `Mobs` SET `MobDescription` = 'Rusa' WHERE `MobId` = 681; -- Deer
UPDATE `Mobs` SET `MobDescription` = 'Kelinci' WHERE `MobId` = 682; -- Rabbit
UPDATE `Mobs` SET `MobDescription` = 'Tupai' WHERE `MobId` = 683; -- Squirrel
UPDATE `Mobs` SET `MobDescription` = 'Domba' WHERE `MobId` = 684; -- Sheep
UPDATE `Mobs` SET `MobDescription` = 'Ular' WHERE `MobId` = 685; -- Snake
UPDATE `Mobs` SET `MobDescription` = 'Harimau' WHERE `MobId` = 686; -- Tiger
UPDATE `Mobs` SET `MobDescription` = 'Mencit Darah' WHERE `MobId` = 687; -- Blood mouse
UPDATE `Mobs` SET `MobDescription` = 'Tikus Darah' WHERE `MobId` = 688; -- Blood rat
UPDATE `Mobs` SET `MobDescription` = 'Ular' WHERE `MobId` = 689; -- Snake
UPDATE `Mobs` SET `MobDescription` = 'Rubah Berekor Sembilan' WHERE `MobId` = 693; -- Nine-tailed fox
UPDATE `Mobs` SET `MobDescription` = 'Serigala Merah' WHERE `MobId` = 708; -- Red wolf
UPDATE `Mobs` SET `MobDescription` = 'Tupai Hijau' WHERE `MobId` = 709; -- Green squirrel
UPDATE `Mobs` SET `MobDescription` = 'Lobster Hitam' WHERE `MobId` = 710; -- Black lobster
UPDATE `Mobs` SET `MobDescription` = 'Kelinci' WHERE `MobId` = 711; -- Rabbit
UPDATE `Mobs` SET `MobDescription` = 'Rubah' WHERE `MobId` = 712; -- Fox
UPDATE `Mobs` SET `MobDescription` = 'Beruang' WHERE `MobId` = 713; -- Bear
UPDATE `Mobs` SET `MobDescription` = 'Harimau' WHERE `MobId` = 714; -- Tiger
UPDATE `Mobs` SET `MobDescription` = 'Terwelu Abisal' WHERE `MobId` = 733; -- Abyssal Hare
UPDATE `Mobs` SET `MobDescription` = 'Kelinci Abisal' WHERE `MobId` = 734; -- Abyssal Rabbit
UPDATE `Mobs` SET `MobDescription` = 'Monyet Abisal' WHERE `MobId` = 738; -- abyssal monkey1
UPDATE `Mobs` SET `MobDescription` = 'Anjing Abisal' WHERE `MobId` = 744; -- abyssal dog1
UPDATE `Mobs` SET `MobDescription` = 'Anak Ayam Abisal' WHERE `MobId` = 751; -- Abyssal Chick
UPDATE `Mobs` SET `MobDescription` = 'Ayam Abisal' WHERE `MobId` = 752; -- Abyssal Chicken
UPDATE `Mobs` SET `MobDescription` = 'Tikus Abisal' WHERE `MobId` = 756; -- abyssal rat1
UPDATE `Mobs` SET `MobDescription` = 'Kuda Abisal' WHERE `MobId` = 762; -- abyssal horse1
UPDATE `Mobs` SET `MobDescription` = 'Sapi Abisal' WHERE `MobId` = 768; -- abyssal ox1
UPDATE `Mobs` SET `MobDescription` = 'Babi Abisal' WHERE `MobId` = 774; -- abyssal pig1
UPDATE `Mobs` SET `MobDescription` = 'Domba Abisal' WHERE `MobId` = 786; -- abyssal sheep1
UPDATE `Mobs` SET `MobDescription` = 'Wyrm Abisal' WHERE `MobId` = 800; -- Abyssal Wyrm
UPDATE `Mobs` SET `MobDescription` = 'Pengawal Harimau' WHERE `MobId` = 900; -- Tiger sentry
UPDATE `Mobs` SET `MobDescription` = 'Penjaga Harimau' WHERE `MobId` = 901; -- Tiger guardian
UPDATE `Mobs` SET `MobDescription` = 'Pembela Harimau' WHERE `MobId` = 902; -- Tiger defender
