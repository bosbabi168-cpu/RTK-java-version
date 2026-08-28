MerchantNpc = {
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

		if player.class == 2 and (player.quest["subpath_trials"] == 0 or player.quest["subpath_trials"] == 11) and (player.gameRegistry["subpaths_released"] == 0 or player.gmLevel == 99) then
			table.insert(opts, "Bergabung dengan Merchant")
		end

		if player.quest["subpath_trials"] == 11 then
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
		elseif menu == "Bergabung dengan Merchant" then
			if player.level < 50 then
				player:dialogSeq(
					{t, "Kau masih terlalu muda untuk bergabung sekarang."},
					0
				)
			end

			if not player:karmaCheck("dog") then
				player:dialogSeq(
					{
						t,
						"Jiwamu terlalu kotor. Perbaiki karmamu lalu kembalilah."
					},
					0
				)
				return
			end

			if player.quest["subpath_trials"] == 0 then
				local join = player:menuString(
					"Apakah kau ingin bergabung dengan para Merchant?",
					{"Ya", "Tidak"}
				)
				if join == "Ya" then
					player.quest["subpath_trials"] = 11
					player:dialogSeq(
						{
							t,
							"Tuntaskan ujian-ujianku untuk memahami jalan para Merchant."
						},
						0
					)
				else
					player:dialogSeq({t, "Jangan buang waktuku."}, 0)
				end
			elseif player.quest["subpath_trials"] == 11 then
			else
				player:dialogSeq(
					{
						t,
						"Kau harus meninggalkan ujianmu yang lain sebelum memulai yang ini."
					},
					0
				)
			end
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
}
