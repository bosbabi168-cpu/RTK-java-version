local _getWaypointId = function(player, npc)
	local waypointIdByMap = {
		[1144] = "thane"
	}

	local waypointId = waypointIdByMap[npc.m]

	if (not waypointId) then
		return "kugnae"
	end

	return waypointId
end

SmithNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local options = {
			"Beli",
			"Jual",
			"Perbaiki Barang",
			"Perbaiki Semua Barang"
		}

		if npc.mapTitle == "Beard Smith" or npc.mapTitle == "Dok Smith" then
			table.insert(options, "Metal Refinement")
			table.insert(options, "Metalworking Devotion")
			table.insert(options, "Metal Preparation")
		end

		if npc.mapTitle == "Gruff Smith" then
			table.insert(options, "Keahlian Kerajinan")
			table.insert(options, "I'm Smelting!")
			table.insert(options, "Smelting Specialization")

			if (player.level >= 50) then
				table.insert(options,"Gruff ring")
			end
		end

		if npc.mapTitle == "Thane's Cave" then
			table.insert(options, "Keahlian Kerajinan")
			table.insert(options, "Mine, Mine, Mine")
		end

		local waypointId = _getWaypointId(player, npc)

		if (not Waypoint.isEnabled(player, waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			options
		)

		if choice == "Beli" then
			SmithNpc.buy(player, npc)
		elseif choice == "Jual" then
			SmithNpc.sell(player, npc)
		elseif choice == "Perbaiki Barang" then
			player:repairExtend()
		elseif choice == "Perbaiki Semua Barang" then
			player:repairAll(npc)
		elseif choice == "Keahlian Kerajinan" then
			generalNPC.crafting_skills(player, npc)
		elseif choice == "I'm Smelting!" then
			SmithNpc.imsmelting(player, npc)
		elseif choice == "Smelting Specialization" then
			SmithNpc.smelting_specialization(player, npc)
		elseif choice == "Mine, Mine, Mine" then
			SmithNpc.mineminemine(player, npc)
		elseif choice == "Metal Refinement" then
			SmithNpc.metalRefinement(player, npc)
		elseif choice == "Metalworking Devotion" then
			SmithNpc.metalworkingDevotion(player, npc)
		elseif choice == "Gruff ring" then
			SmithNpc.gruffRing(player, npc)
		elseif choice == "Metal Preparation" then
			SmithNpc.metalPreparation(player, npc)
		elseif choice == "Waypoint" then
			Waypoint.add(player, npc, waypointId)
		end
	end),

	buy = function(player, npc)
		if npc.mapTitle == "Thane's Cave" then
			local items = {"mining_pick"}
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				items
			)
			return
		end

		local str = "I think I can accomodate some of the things you need. What would you like?"

		local buyopts = {
			"Projectiles",
			"Barang lainnya",
			"Peasant clothes",
			"Male helms",
			"Female helmets",
			"Warrior's platemail",
			"Rogue's armor",
			"Warrior's scalemail"
		}

		local pclothes = {
			"war_platemail",
			"spring_mail_dress",
			"spring_war_dress",
			"scale_mail",
			"merchant_armor",
			"spring_armor_dress"
		}

		local projectiles = {
			"spring_bow"
		}

		local others = {
			"wooden_saber",
			"wooden_sword",
			"viperhead_woodsaber",
			"viperhead_woodsword",
			"steel_dagger",
			"steel_saber",
			"steel_sword",
			"steel_blade"
		}

		local mhelms = {
			"merchant_helm",
			"farmer_helm",
			"royal_helm",
			"sky_helm",
			"ancient_helm",
			"blood_helm",
			"earth_helm"
		}

		local fhelmets = {
			"spring_helmet",
			"summer_helmet",
			"autumn_helmet",
			"winter_helmet",
			"ancient_helmet",
			"blood_helmet",
			"earth_helmet"
		}

		local warrior_platemail = {
			"jade_war_platemail",
			"royal_war_platemail",
			"sky_war_platemail",
			"ancient_war_platemail",
			"blood_war_platemail",
			"earth_war_platemail",
			"summer_war_dress",
			"autumn_war_dress",
			"winter_war_dress",
			"ancient_war_dress",
			"blood_war_dress",
			"earth_war_dress"
		}

		local rogue_armor = {
			"farmer_armor",
			"royal_armor",
			"sky_armor",
			"ancient_armor",
			"blood_armor",
			"earth_armor",
			"summer_armor_dress",
			"autumn_armor_dress",
			"winter_armor_dress",
			"ancient_armor_dress",
			"blood_armor_dress",
			"earth_armor_dress"
		}

		local warrior_scalemail = {
			"jade_scale_mail",
			"royal_scale_mail",
			"sky_scale_mail",
			"ancient_scale_mail",
			"blood_scale_mail",
			"earth_scale_mail",
			"summer_mail_dress",
			"autumn_mail_dress",
			"winter_mail_dress",
			"ancient_mail_dress",
			"blood_mail_dress",
			"earth_mail_dress"
		}

		local subchoice = player:menuString(
			"Apa yang ingin kau beli hari ini?",
			buyopts
		)

		if subchoice == "Peasant clothes" then
			player:buyExtend(str, pclothes)
		elseif subchoice == "Projectiles" then
			player:buyExtend(str, projectiles)
		elseif subchoice == "Barang lainnya" then
			player:buyExtend(str, others)
		elseif subchoice == "Male helms" then
			player:buyExtend(str, mhelms)
		elseif subchoice == "Female helmets" then
			player:buyExtend(str, fhelmets)
		elseif subchoice == "Warrior's platemail" then
			player:buyExtend(str, warrior_platemail)
		elseif subchoice == "Rogue's armor" then
			player:buyExtend(str, rogue_armor)
		elseif subchoice == "Warrior's scalemail" then
			player:buyExtend(str, warrior_scalemail)
		end
	end,

	sell = function(player, npc)
		local sellitems = SmithNpc.sellItems(npc)
		player:sellExtend("What are you willing to sell today?", sellitems)
	end,

	mineminemine = function(player, npc)
		Tools.configureDialog(player, npc)

		player:dialogSeq(
			{
				"Seorang lelaki compang-camping berwajah agak sinting menyambutmu riang. Wah, halo! Tentu, aku bisa bercerita soal menambang.",
				"Pertama, tempat terbaik menambang jelas daerah berbatu. Pegunungan di utara dan timur sini paling bagus.",
				"Untuk menambang kau cuma butuh satu benda: beliung tambang. Kebetulan aku menjual barang itu. Untung sekali!",
				"Menambang itu sederhana. Ayunkan beliungmu ke bijih.",
				"Kalau tidak menemukan apa-apa, berpindahlah dan coba tempat lain.",
				"Pakai beliungmu untuk melepaskan apa pun yang kau temukan.",
				"Ada beberapa kiat kecil dalam pekerjaan ini, tetapi biar kau temukan sendiri.",
				"Oh, satu lagi. Kadang beliungmu bisa patah. Jangan khawatir! Kembali saja ke sini dan beli yang baru dariku!"
			},
			0
		)

		return
	end,

	imsmelting = function(player, npc)
		Tools.configureDialog(player, npc)

		player:dialogSeq(
			{
				"Kau tertarik pada peleburan, ya? Boleh, aku punya waktu sebentar. Melebur bukan kerajinan untuk orang lembek.",
				"Tapi aku tidak mau disalahkan kalau kau melukai diri sendiri, paham? Kau butuh perjanjian peleburan sebelum pandai besi waras mana pun mengizinkanmu mendekati bara.",
				"Menempa mengubah bijih atau logam bekas menjadi batangan logam yang berguna. Makin baik mutu bijihnya, makin baik pula hasilnya.",
				"Kalau kau punya lebih banyak untuk dilebur, katakan 'lebur' padaku dan kita mulai."
			},
			0
		)

		return
	end,

	smelting_specialization = function(player, npc)
		Tools.configureDialog(player, npc)

		if crafting.checkSpecializationLegend(player, "smelting") then
			player:dialogSeq({"Kau sudah mendalami Smelting."}, 0)
			return
		end

		crafting.checkSpecialization(player, npc, "weaving")
		crafting.checkSpecialization(player, npc, "gemcutting")

		player:dialogSeq({"Peleburan membuat logam dari bijih. Kau mau mendalami peleburan? ((Kau harus mendalaminya untuk bisa melampaui tingkat 'Accomplished'.))"}, 1)

		crafting.addSpecialization(player, npc, "smelting")
	end,

	metalRefinement = function(player, npc)
		Tools.configureDialog(player, npc)

		player:dialogSeq(
			{
				"Pengerjaan logam itu sangat berguna. Dengan keahlian ini kau bisa membuat senjata logam bernilai tinggi. Kalau kau membawa logam, katakan 'logam' padaku dan akan kubantu.",
				"Bekerjalah bersama penjahit, dan kau juga bisa membuat zirah. Kau menyiapkan logamnya lebih dulu, penjahit menyiapkan kainnya."
			},
			1
		)

		player:dialogSeq({"Aku bisa membantumu menyiapkan logam untuk pembuatan zirah. Katakan kau ingin 'tempa zirah' setelah logammu siap dan penjahitmu sudah bersedia."}, 0)
	end,

	metalworkingDevotion = function(player, npc)
		Tools.configureDialog(player, npc)

		if (player.level < 25) then
			player:dialogSeq({"Kau belum siap menekuni satu kerajinan. Kembalilah nanti."}, 0)
			return
		end

		if crafting.checkSkillLegend(player, "metalworking") then
			player:dialogSeq({"Kau sudah menekuni ilmu Metalworking."}, 0)
			return
		end

		crafting.checkSkill(player, npc, "woodworking")
		crafting.checkSkill(player, npc, "jewelry making")
		crafting.checkSkill(player, npc, "tailoring")

		player:dialogSeq({"Ahli logam bisa membuat senjata logam, dan dengan bantuan penjahit bisa membuat zirah. Kau ingin menjadi ahli logam?"}, 1)

		crafting.addSkill(player, npc, "metalworking")
	end,

	buyItems = function(npc)
		local pclothes = {
			"war_platemail",
			"spring_mail_dress",
			"spring_war_dress",
			"scale_mail",
			"merchant_armor",
			"spring_armor_dress"
		}
		local projectiles = {"spring_bow"}

		local others = {
			"wooden_saber",
			"wooden_sword",
			"viperhead_woodsaber",
			"viperhead_woodsword",
			"steel_dagger",
			"steel_saber",
			"steel_sword",
			"steel_blade"
		}

		local mhelms = {
			"merchant_helm",
			"farmer_helm",
			"royal_helm",
			"sky_helm",
			"ancient_helm",
			"blood_helm",
			"earth_helm"
		}
		local fhelmets = {
			"spring_helmet",
			"summer_helmet",
			"autumn_helmet",
			"winter_helmet",
			"ancient_helmet",
			"blood_helmet",
			"earth_helmet"
		}
		local warrior_platemail = {
			"jade_war_platemail",
			"royal_war_platemail",
			"sky_war_platemail",
			"ancient_war_platemail",
			"blood_war_platemail",
			"earth_war_platemail",
			"summer_war_dress",
			"autumn_war_dress",
			"winter_war_dress",
			"ancient_war_dress",
			"blood_war_dress",
			"earth_war_dress"
		}
		local rogue_armor = {
			"farmer_armor",
			"royal_armor",
			"sky_armor",
			"ancient_armor",
			"blood_armor",
			"earth_armor",
			"summer_armor_dress",
			"autumn_armor_dress",
			"winter_armor_dress",
			"ancient_armor_dress",
			"blood_armor_dress",
			"earth_armor_dress"
		}
		local warrior_scalemail = {
			"jade_scale_mail",
			"royal_scale_mail",
			"sky_scale_mail",
			"ancient_scale_mail",
			"blood_scale_mail",
			"earth_scale_mail",
			"summer_mail_dress",
			"autumn_mail_dress",
			"winter_mail_dress",
			"ancient_mail_dress",
			"blood_mail_dress",
			"earth_mail_dress"
		}
		local items = {}

		if npc.mapTitle == "Thane's Cave" then
			table.insert(items, "mining_pick")
		end

		for i = 1, #pclothes do
			table.insert(items, pclothes[i])
		end
		for i = 1, #projectiles do
			table.insert(items, projectiles[i])
		end
		for i = 1, #others do
			table.insert(items, others[i])
		end
		for i = 1, #mhelms do
			table.insert(items, mhelms[i])
		end
		for i = 1, #fhelmets do
			table.insert(items, fhelmets[i])
		end
		for i = 1, #warrior_platemail do
			table.insert(items, warrior_platemail[i])
		end
		for i = 1, #rogue_armor do
			table.insert(items, rogue_armor[i])
		end
		for i = 1, #warrior_scalemail do
			table.insert(items, warrior_scalemail[i])
		end

		return items
	end,

	sellItems = function(npc)
		local items = SmithNpc.buyItems(npc)

		if (npc.mapTitle == "Thane's Cave") then
			-- Thane will only buy mining picks and ores
			items = {"mining_pick", "ore_poor", "ore_med", "ore_high", "ore_very_high", "silver_ore", "gold_ore"}
			return items
		end

		table.insert(items, "fine_steel_dagger")
		table.insert(items, "fine_steel_saber")
		table.insert(items, "fine_steel_sword")
		table.insert(items, "fine_steel_blade")
		table.insert(items, "slag")

		if (Config.bossDropSalesEnabled) then
			table.insert(items, "titanium_blade")
			table.insert(items, "swift_sword")
			table.insert(items, "star_helm")
			table.insert(items, "star_helmet")
			table.insert(items, "moon_helm")
			table.insert(items, "moon_helmet")
			table.insert(items, "sun_helm")
			table.insert(items, "sun_helmet")
			table.insert(items, "battle_helm")
		end

		return items
	end,

	gruffRing = function(player, npc)
		Tools.configureDialog(player, npc)

		if player.quest["gruff_ring"] == 0 then
			player.quest["gruff_ring"] = 1
			player:dialogSeq({"Halo, orang kasar. Aku Gruff. Aku bisa membuatkanmu cincin kalau kau cukup kasar."}, 1)
			player:flushKills("trapdoor_spider")
			player:dialogSeq({"Temukan Trapdoor spider. Bunuh dan bawakan aku kapak Hunang."}, 1)
		end

		if player.quest["gruff_ring"] == 1 then
			if player:killCount("trapdoor_spider") == 0 or player:hasItem("hunangs_axe", 1) ~= true then
				player:dialogSeq({"Beri tahu aku kalau kapak Hunang sudah kau dapat dan Trapdoor spider sudah kau bunuh."}, 0)
				return
			end

			player:removeItem("hunangs_axe", 1)
			player.quest["gruff_ring"] = 2
			player:dialogSeq({"Terima kasih! Sekarang cari Monkey yang menggenggam Ambrosia. Bawakan Ambrosia itu, tak usah cerita rincian kasarnya."}, 1)
		end

		if player.quest["gruff_ring"] == 2 then
			if player:hasItem("ambrosia", 1) ~= true then
				player:dialogSeq({"Temui aku lagi kalau Ambrosia-nya sudah kau temukan."}, 0)
				return
			end

			player:removeItem("ambrosia", 1)
			player.quest["gruff_ring"] = 3
			player:dialogSeq({"Terima kasih! Sekarang cari Lan dan belanjakan uang di tokonya. Bulan ini berat baginya. Bawa pulang dua cincin paling rohaninya."}, 1)
		end

		if player.quest["gruff_ring"] == 3 then
			if player:hasItem("exorcist_ring", 2) ~= true then
				player:dialogSeq({"Temui aku lagi kalau dua cincin dari Lan sudah kau dapat."}, 0)
				return
			end

			player:removeItem("exorcist_ring", 2)
			player:dialogSeq({"Terima kasih. Kau memang orang kasar! Ini cincin yang kujanjikan."}, 1)
			player:addItem("gruff_ring", 1)
			player.quest["gruff_ring"] = 0
		end
	end,

	metalPreparation = function(player, npc)
		local smithDialog = Tools.configureDialog(player, npc)
		local metalDialog = {graphic = convertGraphic(291, "item"), color = 0}

		if not crafting.checkSkillLegend(player, "metalworking") then
			player:dialogSeq({smithDialog, "Kau bukan pandai besi."}, 0)
			return
		end

		if os.time() > player.quest["smith_metal_prepared"] then
			if player:hasItem("metal", 3) ~= true then
				player:dialogSeq({metalDialog, "Kau butuh tiga unit logam."}, 0)
				return
			end

			player:removeItem("metal", 3, 9)
			player.quest["smith_metal_prepared"] = os.time() + 3600

			-- 1 hr
			player:dialogSeq({metalDialog, "Seluruh persiapan yang diperlukan sudah selesai. Kau masih harus menuntaskan tugasnya dalam satu jam ke depan."}, 0)
		end

		if os.time() < player.quest["smith_metal_prepared"] then
			player:dialogSeq({smithDialog, "Kau sudah menyiapkan logam; pakai dulu yang itu."}, 0)
			return
		end
	end,

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		local smithDialog = Tools.configureDialog(player, npc)
		local angelDialog = {graphic = convertGraphic(49, "monster"), color = 30}

		if speech == "kimesh" and npc.mapTitle == "Runag Smith" then
			Tools.checkKarma(player)

			if player.class >= 10 or player.class < 5 then
				player:dialogSeq({angelDialog, "Kau harus jadi anggota subjalur NPC untuk menempa senjata."}, 0)
				return
			end

			local choice = player:menuSeq(
				"Menempa senjata menuntut banyak pengorbanan. Kau siap?",
				{"Berkorban adalah kehormatan bagiku.", "Aku telah berbuat salah."},
				{}
			)

			if choice == 1 then
				if player.level < 99 then
					player:dialogSeq({smithDialog, "Kembalilah kalau kau sudah mencapai pencerahan ke-99."}, 0)
					return
				end

				if player.baseHealth < 100 or player.baseMagic < 100 then
					player:dialogSeq({smithDialog, "Kau harus menaikkan Vita dan Mana-mu masing-masing di atas 100."}, 0)
					return
				end

				if player.baseWill < 50 or player.baseMight < 50 or player.baseGrace < 50 then
					player:dialogSeq({smithDialog, "Kembalilah kalau might, will, dan grace dasarmu masing-masing sudah minimal 50 angka."}, 0)
					return
				end

				if player.exp < 200000000 then
					player:dialogSeq({angelDialog, "Pengalamanmu tidak cukup untuk ini."}, 0)
					return
				end

				local items = {}

				if player.class == 6 then
					items = {
						"chung_ryong_scale",
						"enchanted_chung_ryong_scale",
						"il_san_chung_ryong_scale",
						"ee_san_chung_ryong_scale",
						"sam_san_chung_ryong_scale",
						"sa_san_chung_ryong_scale"
					}
				elseif player.class == 7 then
					items = {
						"nimble_blade",
						"enchanted_nimble_blade",
						"il_san_nimble_blade",
						"ee_san_nimble_blade",
						"sam_san_nimble_blade",
						"sa_san_nimble_blade"
					}
				elseif player.class == 8 then
					items = {
						"ju_jak_staff",
						"enchanted_ju_jak_staff",
						"il_san_ju_jak_staff",
						"ee_san_ju_jak_staff",
						"sam_san_ju_jak_staff",
						"sa_san_ju_jak_staff"
					}
				elseif player.class == 9 then
					items = {
						"life_lance",
						"enchanted_life_lance",
						"il_san_life_lance",
						"ee_san_life_lance",
						"sam_san_life_lance",
						"sa_san_life_lance"
					}
				end

				if (player.mark == 0 and (player.baseHealth >= 80000 or player.baseMagic >= 40000)) then
					item = items[2]
				elseif player.mark == 0 then
					item = items[1]
				elseif player.mark == 1 then
					item = items[3]
				elseif player.mark == 2 then
					item = items[4]
				elseif player.mark == 3 then
					item = items[5]
				elseif player.mark == 4 then
					item = items[6]
				end

				player:addItem(item, 1, 0, player.ID)

				player.baseHealth = player.baseHealth - 100
				player.baseMagic = player.baseMagic - 100

				player.registry["baseHealth"] = player.baseHealth
				player.registry["baseMagic"] = player.baseMagic

				player.baseWill = player.baseWill - 3
				player.baseGrace = player.baseGrace - 3
				player.baseMight = player.baseMight - 3

				player.registry["baseHealth"] = player.baseHealth
				player.registry["baseMagic"] = player.baseMagic
				player.registry["baseWill"] = player.baseWill
				player.registry["baseGrace"] = player.baseGrace
				player.registry["baseMight"] = player.baseMight

				player.exp = player.exp - 200000000
				player:sendStatus()
				player:calcStat()

				player:dialogSeq({angelDialog, "Pengorbanan telah dilakukan. Sebuah senjata pun ditempa."}, 0)
			elseif choice == 2 then
				player:dialogSeq(
					{angelDialog, "Panggil aku kapan saja kalau kau butuh bantuanku."}, 0)
			end
		end

		if speech == "kiriman khusus" and npc.mapTitle == "Gruff Smith" and player.quest["spy_trials"] == 1 then
			player.quest["spy_trials"] = 2

			player:dialogSeq(
				{
					smithDialog,
					"** Gruff terus bekerja, tanpa mengangkat wajah ke arahmu **",
					"Ya, ya, taruh saja 10 bijih bermutu tinggi itu di meja.",
					" ** Ia kembali menekuni pekerjaannya **"
				},
				0
			)

		elseif speech == "kiriman khusus" and npc.mapTitle == "Gruff Smith" and player.quest["spy_trials"] == 2 then
			if player:hasItem("ore_high", 10) == true then
				player:removeItem("ore_high", 10)
				player.quest["spy_trials"] = 3

				player:dialogSeq(
					{
						smithDialog,
						"** Kau meletakkan bijih itu di meja bersama tanda gagak **",
						"** Gruff cepat-cepat menyakukan tanda itu dan melirik gugup ke pintu dan jendela **",
						"Baik, tanda ini mungkin sudah kau dapat, tetapi kau masih butuh kata sandi cabang Sanhae Guild. Kau punya?"
					},
					0
				)
			else
				player:dialogSeq(
					{
						smithDialog,
						"** Gruff terus bekerja, tanpa mengangkat wajah ke arahmu **",
						"Ya, ya, taruh saja 10 bijih bermutu tinggi itu di meja.",
						" ** Ia kembali menekuni pekerjaannya **"
					},
					0
				)
			end
		elseif speech == "kiriman khusus" and npc.mapTitle == "Gruff Smith" and player.quest["spy_trials"] == 3 then
			player:dialogSeq(
				{
					smithDialog,
					"Jadi, Nak, apa kata sandinya?",
					"** Gruff menempelkan satu jari di pipinya **"
				},
				0
			)

			local response = player:inputSeq(
				"What is the codeword?",
				"",
				"",
				{},
				{}
			)

			local poisonedBraceletDialog = {
				graphic = Item("poisoned_bracelet").icon,
				color = Item("poisoned_bracelet").iconC
			}

			if string.lower(response) == "nightbreeze" then
				player.registry["spy_subterfuge"] = player.registry["spy_subterfuge"] + 1
				player.quest["spy_trials"] = 4
				player:removeItem("suspicious_note", 1)

				player:dialogSeq(
					{
						smithDialog,
						"Bagus, berarti kau memang bagian dari guild kami, tidak seperti orang lain yang muncul belakangan ini. Entah apa maunya dia.",
						"** Si pandai besi menutup dan mengunci pintu lagi, mengintip ke luar jendela sebelum kembali ke meja **",
						"Pekerjaan pandai besi ini kedokku, yang kulakukan siang hari supaya aku bisa melakukan... hal lain secara diam-diam.",
						"Semua orang di guild kami punya kedok - kurasa kau juga.",
						"Kedok menjaga pekerjaan kami yang lain tetap dalam bayang-bayang. Nama asliku pun bukan Gruff...",
						"Tidak ada yang mencurigai petani sederhana sebagai pembunuh bayaran, atau pustakawan tua sebagai penyelundup.",
						"Kedok menjaga kami tetap aman; itulah topeng yang kami pakai untuk mengalihkan perhatian. Kau butuh satu kedok sementara untuk tugas yang akan kau jalani."
					},
					0
				)

				player:addItem("poisoned_bracelet", 1)
				player:removeLegendbyName("spy_subterfuge")

				player:addLegend(
					"Deceived " .. player.registry["spy_subterfuge"] .. " kali dengan tipu muslihat",
					"spy_subterfuge",
					22,
					128
				)

				player.quest["spy_trial_bracelet_time"] = os.time()

				player:dialogSeq(
					{
						poisonedBraceletDialog,
						"** Gruff menyerahkan gelang kecil dengan ujung runcing yang sedikit menonjol **",
						"Hati-hati dengan benda ini, jangan sampai ujungnya menusuk kulitmu."
					},
					0
				)

				player:dialogSeq(
					{
						smithDialog,
						"Rekan kami pemilik toko ramuan, Pitch, berhasil membuat racun mematikan yang ada di dalam gelang itu.",
						"Begitu ujungnya masuk ke daging, racunnya akan mengalir keluar dari gelang lewat ujung runcing itu.",
						"Tugasmu mengantarkan gelang itu, atau tepatnya isinya, kepada seorang anggota Istana Kekaisaran yang sering berjudi di kasino bawah tanah.",
						"Ia menentang Guild dan mengancam membongkar kedok beberapa rekan kami di Sanhae.",
						"Ancaman ini harus dilumpuhkan, dan itu awal yang bagus untuk membuktikan dirimu di Guild.",
						"Pergilah ke penjahit Guild kami, Lin, di ujung jalan ini, dan mintalah diukur untuk pakaian yang sesuai dengan tugas ini.",
						"Katakan padanya kau butuh pakaian untuk 'Acara Khusus', dan ia akan menjelaskan selebihnya.",
						"Kalau kau butuh gelang lagi, kembalilah ke sini dan minta, tetapi membuatnya perlu waktu dan bijih."
					},
					0
				)
			else
				if player.quest["spy_trial_note"] == 0 then
					player.quest["spy_trial_note"] = 1
					player:addItem("suspicious_note", 1)
					player:sendMinitext("Seorang kurir berpenampilan biasa menyerempet bahumu, dan kau merasakan sesuatu terselip ke sakumu.")
				end

				player:dialogSeq({smithDialog, "Sudah kuduga, kita memang tidak ada urusan! Kembalilah ke jalanan, Nak..."}, 0)
			end
		end

		if speech == "batu bara" and npc.mapTitle == "Gruff Smith" then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					smithDialog,
					"Kau mencari batu bara?",
					"Tentu, aku punya banyak, perlu untuk menjaga tungkuku tetap menyala."
				},
				1
			)

			local choice = player:menuSeq(
				"Mau beli sekeping seharga 20 emas?",
				{"Ya, silakan.", "Tidak, terima kasih."},
				{}
			)

			if choice == 1 then
				if player.money < 20 then
					player:dialogSeq({smithDialog, "Berani-beraninya kau merampokku? Bawa bangkaimu keluar dari sini sebelum kutusuk matamu dengan besi panas!"}, 0)
					return
				end

				player.money = player.money - 20
				player:sendStatus()
				player:addItem("coal", 1)

				local choice2 = player:menuSeq(
					"Ini dia... pasti sekarang kau perlu tahu cara menyalakannya, kan?",
					{"Uhh.. yeah", "Tidak, aku sudah tahu!"},
					{}
				)

				if choice2 == 1 then
					player:dialogSeq({smithDialog, "Batu bara memang murah, tetapi pengetahuan tidak. Rahasia itu harganya 5.000."}, 1)

					local choice3 = player:menuSeq(
						"Kau mau membeli pengetahuan itu seharga 5.000 emas?",
						{"Baiklah, ya.", "Tidak sudi!"},
						{}
					)

					if choice3 == 1 then
						if player.money < 5000 then
							player:dialogSeq({smithDialog, "Pengetahuannya ada padaku, tetapi uangmu tidak ada. Kembalilah kalau sudah punya."}, 0)
							return
						end

						player.money = player.money - 5000
						player:sendStatus()

						player:dialogSeq(
							{
								smithDialog,
								"Terima kasih emasnya, sekarang rahasianya... campur saja batu baranya dengan flash dust, itu akan langsung menyalakannya!",
								"Semoga berhasil, kembalilah kapan saja kalau butuh batu bara."
							},
							0
						)
					elseif choice3 == 2 then
						player:dialogSeq({smithDialog, "Nah, kalau suatu saat kau ingin belajar, kembalilah kepadaku."}, 0)
						return
					end
				elseif choice2 == 2 then
					player:dialogSeq({smithDialog, "Bah!"}, 0)
				end
			elseif choice == 2 then
				player:dialogSeq({smithDialog, "Ya sudah, aku juga tidak butuh uang busukmu."}, 0)
			end
		end

		if ((speech == "cincin kasar" or speech == "cincin") and npc.mapTitle == "Gruff Smith") then
			Tools.checkKarma(player)

			if (player.level < 50) then
				return
			end

			SmithNpc.gruffRing(player,npc)
		end

		if speech == "kebajikan" and npc.mapTitle == "Chul Smith" then
			Tools.checkKarma(player)

			if player.quest["wind_armor"] ~= 0 and player.quest["min_clicked"] == 1 and player:hasItem("stardrop", 1) == true then
				player:dialogSeq(
					{
						smithDialog,
						"Jadi Min mengirimmu kepadaku, bahkan ia menyuruhmu membawa Stardrop. Kau pasti sedang menempuh jalan kebajikan.",
						"Kau tampak bingung... bisa kubayangkan, Min dan aku, berkawan. Kau baru tahu separuh legendanya, kawan, tetapi kau sedang menempuh jalan untuk mengetahui sisanya.",
						"Pada waktunya kau akan mengerti.",
						"Jadi kau butuh pedang bintang. Rupanya kawan lama kita masih berjaga sampai sekarang.",
						"Sudah begitu lama; tidak pernah kubayangkan akan tiba hari aku berada dalam keadaan seperti ini.",
						"Jadi serahkan Stardrop itu, dan akan kutempakan satu untukmu.",
						"Ingat baik-baik! Pedang ini lebih merupakan tanda, bukan senjata. Ia tidak bertahan lama dalam pertempuran dan sangat rapuh!",
						"Jagalah baik-baik. Kalau kau berhasil sampai ke penjaga, kau membuktikan dirimu cermat dan layak dipercaya memegang rahasia yang menantimu.",
						"Aku tahu tantangannya besar, dan akan kubuatkan tiga pedang semacam itu untukmu, tidak lebih.",
						"Sebaiknya kau bawa satu saja setiap kali, sampai kau mendapat yang kau cari."
					},
					1
				)

				player:removeItem("stardrop", 1)

				if player.quest["star_swords"] <= 1 then
					player:addItem("star_sword", 1, 0, player.ID)
					player.quest["star_swords"] = player.quest["star_swords"] + 1
					player:dialogSeq({smithDialog, "Ini dia, cantik bukan? Hati-hati sekarang."}, 0)
					return
				elseif player.quest["star_swords"] == 2 then
					player:addItem("star_sword", 1, 0, player.ID)
					player.quest["star_swords"] = player.quest["star_swords"] + 1
					player:dialogSeq({smithDialog, "Ini dia, cantik bukan? Hati-hati, sebab ini pedang terakhir yang kubuat untukmu."}, 0)
					return
				elseif player.quest["star_swords"] >= 3 then
					player:dialogSeq({smithDialog, "Aku sudah membuatkanmu pedang tiga kali seperti yang kujanjikan. Aku tidak bisa membuat lagi."}, 0)
					return
				end

				return
			else
				npc:talk(0, npc.name .. ": Aku tidak paham apa yang kau bicarakan.")
			end
		end

		if (speech == "perisai" and npc.mapTitle == "Chul Smith") then
			Tools.checkKarma(player)

			local baseClass = player.baseClass

			if (baseClass < 1) then
				return
			end

			local legends = {"nagnang_warrior_trial", "dagger_guild_member", "family_nangen_mages", "destroyed_nagnang_evil"}
			local shieldQuest = legends[baseClass]

			if not player:hasLegend(shieldQuest) then
				player:dialogSeq({smithDialog, "Kau tidak tahu apa-apa soal perisai. Kalau kau sudah belajar, mungkin aku akan membantumu."}, 1)
				return
			end

			player:dialogSeq(
				{
					smithDialog,
					"Salam, kulihat kau datang untuk mendapatkan perisai baru.",
					"Perisai ini istimewa karena hanya orang Nagnang yang tahu cara membuatnya.",
					"Sayangnya bahan yang kami butuhkan tidak tersedia di daerah ini.",
					"Kalau kau mau membawakan persediaan bahannya, sebagian akan kupakai untuk membuat perisai baru untukmu."
				},
				1
			)

			local choice = player:menuSeq(
				"Maukah kau memberiku 10 Ginko wood dan satu Metal?",
				{"Ya, ada padaku sekarang.", "Maaf, tidak sekarang."},
				{}
			)

			if choice == 1 then
				if player:hasItem("ginko_wood", 10) ~= true or player:hasItem("metal", 1) ~= true then
					player:dialogSeq({smithDialog, "Kau tidak punya bahan yang kubutuhkan. Ya sudah."}, 0)
					return
				end

				player:removeItem("ginko_wood", 10)
				player:removeItem("metal", 1)

				local shields = {"tall_shield", "round_buckler", "magicians_ward", "essence_charm"}
				local shield = shields[baseClass]
				player:addItem(shield, 1, 0, player.ID)
				player:dialogSeq({smithDialog, "Semoga berhasil, dan terima kasih bahannya."}, 0)
			elseif choice == 2 then
				player:dialogSeq({smithDialog, "Kalau begitu mungkin lain kali. Selamat jalan."}, 0)
				return
			end
		end

		if speech == "laptev" and npc.mapTitle == "Othotsk Blacksmith" then
			Tools.checkKarma(player)

			player:dialogSeq(
				{
					smithDialog,
					"Ssst! Jangan keras-keras, kawan. Ya, aku punya barang langka. Sihir di dalamnya agak rapuh, seperti es tempat benda itu ditemukan.",
					"Benda itu akan terikat pada jiwamu dan hanya kau yang bisa memakainya. Dan setelah kau melempar bola salju terakhirmu, kalau kau paham maksudku, benda itu akan menyertaimu ke alam baka."
				},
				1
			)

			local choice = player:menuSeq(
				"Apa yang bisa kutawarkan padamu?",
				{"Giasomo stick (20,000 gold)", "Frozen spear (400,000 gold)"},
				{}
			)

			if choice == 1 then
				-- giasomo stick
				if os.time() < player.registry["othotsk_timer"] then
					player:dialogSeq({smithDialog, "Maaf, pelanggan setia. Aku tidak punya apa-apa lagi untukmu saat ini. Mungkin seminggu dua minggu lagi."}, 0)
					return
				end

				if player.money < 20000 then
					player:dialogSeq({smithDialog, "Silakan kembali kalau uangnya sudah ada."}, 0)
					return
				end

				player.money = player.money - 20000
				player:sendStatus()
				player:addItem("giasomo_stick", 1, 0, player.ID)
				player.registry["othotsk_timer"] = os.time() + 604800

				player:dialogSeq({smithDialog, "Barang kecil yang aneh, bukan? Ini dia, Giasomo stick milikmu sendiri."}, 0)
			elseif choice == 2 then
				-- frozen spear
				if os.time() < player.registry["othotsk_timer"] then
					player:dialogSeq({smithDialog, "Maaf, pelanggan setia. Aku tidak punya apa-apa lagi untukmu saat ini. Mungkin seminggu dua minggu lagi."}, 0)
					return
				end

				if player.money < 400000 then
					player:dialogSeq({smithDialog, "Silakan kembali kalau uangnya sudah ada."}, 0)
					return
				end

				player.money = player.money - 400000
				player:sendStatus()
				player:addItem("frozen_spear", 1, 0, player.ID)
				player.registry["othotsk_timer"] = os.time() + 604800 * 2

				player:dialogSeq({smithDialog, "Belum pernah kulihat senjata seaneh ini... Ogre memang makhluk ganjil. Ini Frozen spear-mu. Semoga kau suka."}, 0)
			end
		end

		if speech == "tempa logam" and npc.mapTitle == "Gruff Smith" then
			if player.quest["forgotten_path"] == 6 then
				player.quest["forgotten_path"] = 7
				player:dialogSeq(
					{
						smithDialog,
						"Ya, aku bisa menempa logam.\n\nUntuk apa kau butuh logam tempa?",
						"Bola katamu, bola macam apa?",
						"Kau butuh logam khusus untuk bola sihir; bicaralah dengan kawanku Thane di belantara, mungkin ia bisa membantu."
					},
					1
				)

				return
			end
		end

		if npc.mapTitle == "Thane's Cave" then
			if speech == "special metal" and player.quest["forgotten_path"] == 7 then
				player.quest["forgotten_path"] = 8

				player:dialogSeq(
					{
						smithDialog,
						"Kau butuh logam khusus?",
						"Yah... aku punya logam aneh yang kadang kutemukan jauh di dalam tanah.",
						"Warnanya biru ganjil dan bercahaya.\n\nUntuk apa kau membutuhkannya?"
					},
					1
				)

				return
			end
			if speech == "bola logam" then
				if player.quest["forgotten_path"] == 8 then
					player.quest["forgotten_path"] = 9

					player:dialogSeq(
						{
							smithDialog,
							"Baiklah, aku tawarkan kesepakatan.",
							"Kalau kau bisa mengumpulkan 5 bijih bermutu rendah, sedang, dan tinggi untukku, logam aneh ini kuberikan padamu.",
							"Setuju?\n\nBeri tahu aku kalau seluruh bijihnya sudah kau punya."
						},
						1
					)

					return
				elseif player.quest["forgotten_path"] == 9 then
					if (player:hasItem("ore_poor", 5) == true and player:hasItem("ore_med", 5) == true and player:hasItem("ore_high", 5) == true) then
						player:removeItem("ore_poor", 5)
						player:removeItem("ore_med", 5)
						player:removeItem("ore_high", 5)
						player.quest["forgotten_path"] = 10

						player:dialogSeq({smithDialog, "Terima kasih, ambil logam aneh ini. Semoga berhasil!"}, 0)

						return
					else
						player:dialogSeq({smithDialog, "Nah... mana barangnya?"}, 0)
						return
					end
				end
			end
		end

		if npc.mapTitle == "Gruff Smith" and speech == "lebur" then
			crafting.craftingDialog(player, npc, speech)
		end

		if (npc.mapTitle == "Beard Smith" or npc.mapTitle == "Dok Smith") and speech == "logam" then
			crafting.craftingDialog(player, npc, speech)
		end

		if (npc.mapTitle == "Beard Smith" or npc.mapTitle == "Dok Smith") and speech == "tempa zirah" then
			crafting.craftingDialog(player, npc, speech)
		end

		if (npc.mapTitle == "Beard Smith" or npc.mapTitle == "Dok Smith") and speech == "siapkan" then
			SmithNpc.metalPreparation(player, npc)
		end

		local waypointId = _getWaypointId(player, npc)

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, waypointId)) then
			Waypoint.add(player, npc, waypointId)
			return
		end
	end)
}
