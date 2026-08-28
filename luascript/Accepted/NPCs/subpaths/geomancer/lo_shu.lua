NpcSubpathGeomancerLoShuNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Beli", "Jual", "Titipkan Barang", "Ambil Barang"}

		if player.money > 0 then
			table.insert(opts, "Titipkan Uang")
		end

		if player.bankMoney > 0 then
			table.insert(opts, "Ambil Uang")
		end

		table.insert(opts, "War Paint")
		table.insert(opts, "Observe")
		table.insert(opts, "Reincarnate")

		local buysellopts = {"limestone", "obsidian", "book"}

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
		elseif menu == "Titipkan Uang" then
			player:bankAddMoney(npc)
		elseif menu == "Ambil Uang" then
			player:bankWithdrawMoney(npc)
		elseif menu == "Titipkan Barang" then
			player:showBankDeposit(npc)
		elseif menu == "Ambil Barang" then
			player:showBankWithdraw(npc)
		elseif menu == "War Paint" then
			ArenaMasterNpc.warPaint(player, npc)
		elseif menu == "Reincarnate" then
			general_npc_funcs.reincarnate(player, npc)
		elseif menu == "Observe" then
			general_npc_funcs.observe(player, npc)
		end
	end),

	move = function(npc)
		npc.side = math.random(0, 3)
		npc:sendSide()
	end,

	buyItems = function()
		local buyItems = {"limestone", "obsidian", "book"}
		return buyItems
	end,

	sellItems = function()
		return NpcSubpathGeomancerLoShuNpc.buyItems()
	end
}
