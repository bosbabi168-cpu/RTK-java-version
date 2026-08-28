CartographerNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}

		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Keahlian Kerajinan", "Cartography", "Shadow Stats"}

		local menu = player:menuString(
			"Halo! Apa yang ingin kau lakukan hari ini?",
			opts
		)

		if menu == "Keahlian Kerajinan" then
			generalNPC.crafting_skills(player, npc)
		elseif menu == "Cartography" then
			CartographerNpc.cartographyQuest(player, npc)
		elseif menu == "Shadow Stats" then
			ExpSellerNpc.showShadowMainMenu(player, npc)
		end
	end),

	cartographyQuest = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		-- We shouldn't need this since we have it checking for the combined map
		--if crafting.checkSkillLegend(player,"cartography") then
		--	player:dialogSeq({t,"You have already learned the secrets of cartography."},0)
		--return
		--end

		if player:hasItem("combined_map", 1) == true then
			player:removeItem("combined_map", 1)
			player:addLegend(
				"Menemukan jalan ke Gunung Baekdu (" .. curT() .. ")",
				"mount_baekdu",
				3,
				128
			)
			crafting.skillChanceIncrease(
				player,
				npc,
				"cartography"
			)
		end

		player:dialogSeq(
			{
				t,
				"Selamat datang di Gunung Baekdu yang suci; aku sudah bertahun-tahun menjelajahi daerah ini.",
				"Gunung ini menyimpan energi sihir yang memungkinkanmu menjelajahi berbagai tempat di seluruh negeri.",
				"Kalau kau membawakan 10 potongan peta, akan kubantu menggabungkannya untuk menemukan tempat baru.",
				"Tempat-tempat bersihir itu hanya terbuka bagimu dan kelompokmu untuk waktu yang singkat.",
				"Sebagian tempat mungkin terasa akrab, sebagian lagi baru dan menantang.",
				"Kau akan didorong sampai batas kemampuanmu bila memilih berangkat, tetapi ganjarannya bisa besar.",
				"Seiring naiknya keahlian kartografimu, kau bisa membuka tempat yang lebih sulit dan lebih dalam.",
				"Tempat-tempat itu disesuaikan dengan tingkat tandamu, dan hanya bisa dimasuki dengan tanda yang setara.",
				"Kau mungkin melihat makhluk dari masa lalumu, tetapi berhati-hatilah karena mereka jauh lebih sulit.",
				"Setelah membuat peta, taruh di batu suci, dan anggota grupmu akan mengikutimu masuk dari gunung.",
				"(( Wisdom Star tidak memengaruhi kenaikan keahlian ini ))",
				"Sangat disarankan berangkat bersama satu grup!"
			},
			0
		)

		--[[
	if player.quest["cartographyQuest"] == 0 then

		player.quest["cartographyQuest"] = 1

		player:dialogSeq({t,"Welcome to holy Mountain Baekdu. I see that you brought a map with you.",
			"This mountain has magical energy which will let you explore various locations around the lands.",
			"Bring me 10 map fragments and I will teach you how to find these locations.",
			"These magical locations will only be available to you and your group for a short amount of time.",
			"Some locations may seem familiar, but some may be new and challenging.",
			"You will be pushed to your limits, should you choose to venture, but the rewards may be great."},0)

		return
	elseif player.quest["cartographyQuest"] == 1 then

		if player:hasItem("combined_map",1) ~= true then
			player:dialogSeq({t,"I am missing the combined map"},0)
		return
		end

		player:removeItem("combined_map",1)
		crafting.addSkill(player,npc,"cartography")
	end
]]
		--
	end,

	cartographyDevotion = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if (player.level < 25) then
			player:dialogSeq(
				{
					t,
					"Kau belum siap menekuni satu kerajinan. Kembalilah nanti."
				},
				0
			)
			return
		end

		if crafting.checkSkillLegend(player, "cartography") then
			player:dialogSeq(
				{
					t,
					"Kau sudah menekuni ilmu Cartography."
				},
				0
			)
			return
		end

		player:dialogSeq(
			{
				t,
				"Kartografer bisa membuat peta dengan pengubah biasa, langka, legendaris, dan unik. Kau ingin menjadi kartografer?"
			},
			1
		)

		crafting.addSkill(player, npc, "cartography")
	end,

	onSayClick = async(function(player, npc, speech)
		local speech = string.lower(player.speech)
		if speech == "gambar" or speech == "gambar peta" or speech == "gabung" then
			crafting.craftingDialog(player, npc, speech)
		end
	end),

	buyItems = function()
		local buyItems = {}
		return buyItems
	end,

	sellItems = function()
		local sellItems = CartographerNpc.buyItems()
		return sellItems
	end
}
