TalonNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual"}

		-- 1 - chongun
		-- 2 - barbarian
		-- 3 - do
		-- subpaths released = 0 means subpaths are out, 1 means we're working on it
		--if player.class == 1 and (player.quest["subpath_trials"] == 0 or player.quest["subpath_trials"] == 14) and (player.gameRegistry["subpaths_released"] == 0 or player.gmLevel == 99) then
		--	table.insert(opts, "Join the Chongunate")
		--end

		if player.quest["subpath_trials"] == 19 then
			table.insert(opts, "Abandon Trials")
		end

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
		end
	end),

	action = function(npc)
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
		return self:buyItems()
	end,
	onSayClick = async(function(player, npc, speech)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		local speech = string.lower(player.speech)
		if speech == "pesanan khusus" then
			if player.class == 2 and (player.quest["subpath_trials"] == 0 or player.quest["subpath_trials"] == 19) and (player.gameRegistry["subpaths_released"] == 0 or player.gmLevel == 99) then
				if player.quest["spy_trials"] == 0 then
					player.quest["subpath_trials"] = 19
					player.quest["spy_trials"] = 1
					player:dialogSeq(
						{
							t,
							"Ugh, kau orang kedua yang mereka kirim hari ini untuk pesanan khusus ini... Kalau begitu katakan pada orang-orangmu barangnya sudah kukirim.",
							"Mendapatkan barang itu tidak mudah dan tidak aman, tetapi ia sedang dalam perjalanan untuk disiapkan. Mungkin kau bisa mencegatnya sebelum orang yang satunya.",
							"Mungkin rekan pandai besi kami, Gruff, di tokonya di lembah utara tahu soal Kiriman Khusus itu.",
							"** Talon menyerahkan tanda kecil bergambar timbul seekor gagak **",
							"Berikan ini kepadanya sebagai bukti kau bagian dari kami."
						},
						0
					)
				elseif player.quest["spy_trials"] == 1 then
					player:dialogSeq(
						{
							t,
							"** Ia memalingkan wajah saat kau menyebut kata-kata itu **"
						},
						0
					)
				end
			end
		end
	end),
}
