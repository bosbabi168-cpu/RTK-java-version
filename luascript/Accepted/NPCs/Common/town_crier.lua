TownCrierNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {}

		if npc.mapTitle == "Kugnae" then
			table.insert(opts, "Pindah ke Koguryo")
			table.insert(opts, "Koguryo Honor")
		end

		if npc.mapTitle == "Buya" then
			table.insert(opts, "Pindah ke Buya")
			table.insert(opts, "Buya Defender")
		end

		if npc.mapTitle == "Nagnang" then
			table.insert(opts, "Pindah ke Nagnang")
			table.insert(opts, "Nagnang Defender")
		end

		table.insert(opts, "Broadcast Event")
		table.insert(opts, "Wisdom clothes")

		local choice = player:menuString(
			"Halo! Ada yang bisa kubantu hari ini?",
			opts,
			{}
		)

		if choice == "Pindah ke Koguryo" or choice == "Pindah ke Buya" or choice == "Pindah ke Nagnang" then
			local country = 0

			if npc.mapTitle == "Kugnae" then
				country = 1
			elseif npc.mapTitle == "Buya" then
				country = 2
			elseif npc.mapTitle == "Nagnang" then
				country = 3
			end

			general_npc_funcs.moveToCountry(player, npc, country)
		elseif choice == "Broadcast Event" then
			general_npc_funcs.broadcastEvent(player, npc)
		elseif choice == "Koguryo Honor" then
			TownCrierNpc.koguryoHonor(player, npc)
		elseif choice == "Buya Defender" then
			TownCrierNpc.buyaDefender(player, npc)
		elseif choice == "Nagnang Defender" then
			TownCrierNpc.nagnangDefender(player, npc)
		elseif choice == "Wisdom clothes" then
			general_npc_funcs.wisdomClothes(player, npc)
		end
	end),

	koguryoHonor = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player:dialogSeq(
			{t, "Pangeran Mhul harus lebih dulu menganugerahkan kehormatan ini kepadamu."},
			0
		)
	end,

	buyaDefender = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player:dialogSeq(
			{
				t,
				"Putri Lasahn harus lebih dulu menerima kesetiaanmu, baru kau menjadi pembela Buya."
			},
			0
		)
	end,

	nagnangDefender = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		player:dialogSeq(
			{
				t,
				"Pembela Nagnang berhak mengenakan warna kerajaan kami. Bangsawan Nagnang harus lebih dulu menerima kesetiaanmu, baru kau bisa bersumpah membela kerajaan ini."
			},
			0
		)
	end,

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end)
}
