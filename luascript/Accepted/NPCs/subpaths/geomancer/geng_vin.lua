NpcSubpathGeomancerGengVinNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual", "Geng Vin's Welcome"}

		local buysellopts = {
			"rabbit_meat",
			"meat_scrap",
			"horse_meat",
			"antler",
			"bears_liver",
			"tigers_heart"
		}

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Beli" then
			player:buyExtend(
				"I think I can accomodate some of the things you need. What would you like?",
				buysellopts
			)
		elseif menu == "Jual" then
			player:sellExtend(
				"What are you willing to sell today?",
				buysellopts
			)
		elseif menu == "Geng Vin's Welcome" then
			player:dialogSeq(
				{
					t,
					"Halo dan selamat datang di House of Chi. Para Geomancer mengizinkanku tinggal di Hallowed Pass ini dan beristirahat dari perjalananku di Barat. Sebagai gantinya aku membantu menjaga rumah mereka.",
					"Silakan masuk dan lihat-lihat, mungkin sekalian ambil satu Shu Jing. Aku ingin memberikannya sendiri, tetapi aku dan Naga Bumi tidak selalu sepaham. Aku cepat, tetapi ia lebih cepat...",
					"Kalau kau lapar, aku selalu bersedia berbagi hasil buruanku. Menjadi pemangsa di puncak...*melirik ke dalam House of Chi* ..dekat puncak, ada untungnya."
				},
				0
			)
			return
		end
	end),

	action = function(npc)
		local random = math.random(1, 15)

		if random == 1 then
			npc:talk(0, npc.name .. ": Selamat datang di House of Chi")
		end
	end,

	move = function(npc)
		npc.side = math.random(0, 3)
		npc:sendSide()
	end,

	buyItems = function()
		local buyItems = {
			"rabbit_meat",
			"meat_scrap",
			"horse_meat",
			"antler",
			"bears_liver",
			"tigers_heart"
		}
		return buyItems
	end,

	sellItems = function()
		return NpcSubpathGeomancerGengVinNpc.buyItems()
	end
}
