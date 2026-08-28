local _waypointId = "sel"

SelNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"Keahlian Kerajinan",
			"Seluk-beluk Gemcutting",
			"Gemcutting Specialization",
			"Jewelry Devotion"
		}

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local menu = player:menuString(
			"Halo! Apa yang ingin kau lakukan hari ini?",
			opts
		)

		if menu == "Keahlian Kerajinan" then
			generalNPC.crafting_skills(player, npc)
		elseif menu == "Seluk-beluk Gemcutting" then
			player:dialogSeq(
				{
					"Halo. Kau ingin belajar Gemcutting? Tentu, aku bisa bercerita sedikit.",
					"Gemcutting adalah keahlian manufacturing. Kau mulai dengan satu amber lalu berusaha mengasahnya jadi bentuk yang indah.",
					"Kadang pengasah permata membuat kesalahan. Amber bisa jadi kusam sehingga kurang berharga. Pada hari yang benar-benar buruk, amber itu rusak sama sekali.",
					"Tetapi amber yang sudah diasah terjual jauh lebih mahal, jadi risiko gagalnya sepadan. Seperti keahlian mana pun, butuh banyak kerja untuk benar-benar mahir.",
					"Ada banyak jenis amber, dan sebagian butuh keahlian cukup tinggi untuk diasah."
				},
				0
			)
			return
		elseif menu == "Gemcutting Specialization" then
			SelNpc.gemcutting_specialization(player, npc)
		elseif menu == "Jewelry Devotion" then
			SelNpc.jewelryDevotion(player, npc)
		elseif menu == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		end
	end),

	gemcutting_specialization = function(player, npc)
		Tools.configureDialog(player, npc)

		if crafting.checkSpecializationLegend(player, "gemcutting") then
			player:dialogSeq({"Kau sudah mendalami Gemcutting."}, 0)
			return
		end

		crafting.checkSpecialization(player, npc, "smelting")
		crafting.checkSpecialization(player, npc, "weaving")

		player:dialogSeq({"Pengasah permata mengolah batu langka. Kau mau mendalami gemcutting? ((Kau harus mendalaminya untuk bisa melampaui tingkat 'Accomplished'.))"}, 1)

		crafting.addSpecialization(player, npc, "gemcutting")
	end,

	jewelryDevotion = function(player, npc)
		Tools.configureDialog(player, npc)

		if (player.level < 25) then
			player:dialogSeq({"Kau belum siap menekuni satu kerajinan. Kembalilah nanti."}, 0)
			return
		end

		if crafting.checkSkillLegend(player, "jewelry making") then
			player:dialogSeq({"Kau sudah menekuni ilmu pembuatan perhiasan."}, 0)
			return
		end

		crafting.checkSkill(player, npc, "woodworking")
		crafting.checkSkill(player, npc, "tailoring")
		crafting.checkSkill(player, npc, "metalworking")

		player:dialogSeq({"Perajin perhiasan bisa membuat perhiasan indah dari amber olahan dan emas. Kau ingin menjadi perajin perhiasan?"}, 1)

		crafting.addSkill(player, npc, "jewelry making")
	end,

	onSayClick = async(function(player, npc)
		Tools.configureDialog(player, npc)
		local speech = string.lower(player.speech)

		if (speech == "permata" or speech == "permata") then
			crafting.craftingDialog(player, npc, speech)
			return
		end

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end
	end),

	buyItems = function()
		local buyItems = {}

		local pclothes = {
			"spring_dress",
			"spring_blouse",
			"spring_skirt",
			"spring_gown"
		}
		local rclothes = {
			"summer_blouse",
			"autumn_blouse",
			"winter_blouse",
			"leather_blouse",
			"ancient_blouse",
			"earth_blouse"
		}
		local mdress = {
			"summer_dress",
			"autumn_dress",
			"winter_dress",
			"leather_dress",
			"ancient_dress",
			"earth_dress"
		}
		local mskirt = {
			"summer_skirt",
			"autumn_skirt",
			"winter_skirt",
			"leather_skirt",
			"heart_skirt",
			"earth_skirt"
		}
		local pdraperies = {
			"summer_gown",
			"autumn_gown",
			"winter_gown",
			"leather_gown",
			"ancient_gown",
			"earth_gown"
		}
		local oitems = {"wedding_dress"}

		for i = 1, #pclothes do
			table.insert(buyItems, pclothes[i])
		end
		for i = 1, #rclothes do
			table.insert(buyItems, rclothes[i])
		end
		for i = 1, #mdress do
			table.insert(buyItems, mdress[i])
		end
		for i = 1, #mskirt do
			table.insert(buyItems, mskirt[i])
		end
		for i = 1, #pdraperies do
			table.insert(buyItems, pdraperies[i])
		end
		for i = 1, #oitems do
			table.insert(buyItems, oitems[i])
		end

		return buyItems
	end,

	sellItems = function()
		local sellItems = SelNpc.buyItems()
		return sellItems
	end
}
